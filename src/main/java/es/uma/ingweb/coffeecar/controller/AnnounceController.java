package es.uma.ingweb.coffeecar.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.uma.ingweb.coffeecar.consumers.AnnouncementConsumer;
import es.uma.ingweb.coffeecar.consumers.BusConsumer;
import es.uma.ingweb.coffeecar.consumers.StopConsumer;
import es.uma.ingweb.coffeecar.consumers.UserConsumer;
import es.uma.ingweb.coffeecar.entities.Announce;
import es.uma.ingweb.coffeecar.entities.Bus;
import es.uma.ingweb.coffeecar.entities.BusStop;
import es.uma.ingweb.coffeecar.entities.User;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;


@Controller
public class AnnounceController {

    private final AnnouncementConsumer announcementConsumer;
    private final UserConsumer userConsumer;
    private final StopConsumer stopConsumer;
    private final BusConsumer busConsumer;

    public AnnounceController(AnnouncementConsumer announcementConsumer, UserConsumer userConsumer, StopConsumer stopConsumer, BusConsumer busConsumer) {
        this.announcementConsumer = announcementConsumer;
        this.userConsumer = userConsumer;
        this.stopConsumer = stopConsumer;
        this.busConsumer = busConsumer;
    }

    @PostMapping("createAnnouncement/confirm")
    public String announce(
          @ModelAttribute Announce announce,
          OAuth2AuthenticationToken authenticationToken,
          RedirectAttributes redirectAttrs
    ) {
        User driver = userConsumer.getByEmail(authenticationToken.getPrincipal().getAttribute("email"));
        if (announce.getDescription() == null || announce.getDescription().isEmpty()) {
            announce.setDescription("No hay descripción");
        }

        URI uri = create(announce, driver);

        Announce announceAux = announcementConsumer.getAnnouncementByURI(uri.toString());

        redirectAttrs
                .addFlashAttribute("mensaje", "Agregado correctamente");

        return "redirect:/details?announcementURI=" + announceAux.getLink("self").map(Link::getHref).get();
    }

    @GetMapping("/createAnnouncement")
    public String createAnnouncement(Model model) {
        model.addAttribute("anuncio", new Announce());
        return "createAnnouncement";
    }

    @GetMapping("/details")
    public String announcementDetails(
          @RequestParam(name = "announcementURI") String uri,
          Model model,
          OAuth2AuthenticationToken authenticationToken) {
        Announce announcement = announcementConsumer.getAnnouncementByURI(uri);
        List<BusStop> stops = stopConsumer
              .getNearby(announcement.getDepartureLatitude(), announcement.getDepartureLongitude());
        List<BusStop> stopsArrival = stopConsumer
                .getNearby(announcement.getArrivalLatitude(), announcement.getArrivalLongitude());

        List<Bus> buses = busConsumer.getAll() ;
        User user = userConsumer.getByEmail(authenticationToken.getPrincipal().getAttribute("email"));
        boolean isDriver;
        boolean isPassenger = announcement.getPassengers().contains(user);
        boolean canJoin;
        if(authenticationToken.getPrincipal().getAttribute("email") == "pruebaparaingweb@gmail.com"){
            isDriver = true;
            canJoin = !isPassenger && (announcement.getSeats() > announcement.getPassengers().size());
        }else {
            isDriver = announcement.getDriver()
                    .equals(user);
            canJoin = !isPassenger && (announcement.getSeats() > announcement.getPassengers().size()) && !isDriver;
        }

        model.addAttribute("isDriver", isDriver);
        model.addAttribute("isPassenger", isPassenger);
        model.addAttribute("announcement", announcement);
        model.addAttribute("canJoin",canJoin);
        model.addAttribute("paradas", stops);
        model.addAttribute("buses",buses);
        model.addAttribute("paradasLlegada", stopsArrival);
        return "announcementDetails";
    }

    @PostMapping("details/join")
    public String joinAnnouncement(
            @RequestParam(name = "announcementURI") String uri,
            OAuth2AuthenticationToken authenticationToken,
            RedirectAttributes redirectAttrs
    ){
        Announce announce = announcementConsumer.getAnnouncementByURI(uri);
        User user = userConsumer.getByEmail(authenticationToken.getPrincipal().getAttribute("email"));
        List<User> passengers = announce.getPassengers();
        String announceURI = uri;
        if(!passengers.contains(user)) {
            passengers.add(user);
            announceURI = editOnlyPassengers(announce).toString();

            redirectAttrs
                    .addFlashAttribute("mensaje", "Te has unido al viaje");
        }
        return "redirect:/details?announcementURI=" + announceURI;
    }

    private URI editOnlyPassengers(Announce announce){
        ObjectMapper objectMapper = new ObjectMapper();

        ObjectNode announceNode = objectMapper.valueToTree(announce);
        ArrayNode passengerNode = announceNode.putArray("passengers");
        announce.getPassengers()
                .forEach(
                        user ->
                                passengerNode
                                        .add(user
                                                .getLink("self").map(Link::getHref).get()));

        announceNode.put("driver", announce.getDriver().getLink("self").map(Link::getHref).get());

        String uri = announce.getLink("self").map(Link::getHref).get();

        return announcementConsumer.edit(uri, announceNode);
    }
    private void edit(Announce announce){
        announcementConsumer.edit(announce);
    }

    private URI create(Announce announce, User driver){
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jsonNodeAnnouncement = objectMapper.valueToTree(announce);
        jsonNodeAnnouncement.put("driver", driver.getLink("self").map(Link::getHref).get());

        return announcementConsumer.create(jsonNodeAnnouncement);
    }

    @PostMapping("details/left")
    public String leftAnnouncement(
            @RequestParam(name = "announcementURI") String uri,
            OAuth2AuthenticationToken authenticationToken,
            RedirectAttributes redirectAttrs
    ){
        Announce announce = announcementConsumer.getAnnouncementByURI(uri);
        User user = userConsumer.getByEmail(authenticationToken.getPrincipal().getAttribute("email"));
        List<User> newPassengers = announce.getPassengers();
        boolean successfulRemoval = newPassengers.remove(user);
        String announceURI = uri;
        if(successfulRemoval){
            announce.setPassengers(newPassengers);
            announceURI = editOnlyPassengers(announce).toString();
            redirectAttrs
                    .addFlashAttribute("mensaje", "Has dejado el viaje");
        }else {
            redirectAttrs
                    .addFlashAttribute("mensaje", "No has podido dejarlo o ya no estabas unido");
        }
        return "redirect:/details?announcementURI=" + announceURI;
    }

    @PostMapping("/announcementDelete")
    public String announcementDelete(
            @RequestParam(name = "announcementURI") String uri,
            RedirectAttributes redirectAttrs) {

        announcementConsumer.delete(uri);
        redirectAttrs
                .addFlashAttribute("mensaje", "Eliminado correctamente");
        return "redirect:/";
    }

    // metodo al querer editar un anuncio
    @GetMapping("/editarAnuncio")
    public String editAnnouncement(@RequestParam(name="announcementURI") String uri,
                                   Model model){

            Announce announce = announcementConsumer.getAnnouncementByURI(uri);
            model.addAttribute("announce", announce);
            model.addAttribute("uri",uri);
        return "editAnnouncement";
    }

    @PostMapping("/editarAnuncio/confirm")
    public String changeAnnouncement(@ModelAttribute Announce announce,@RequestParam("announcementURI") String uri,
                                     Model model, OAuth2AuthenticationToken authenticationToken){
        if (announce.getDescription() == null || announce.getDescription().isEmpty()) {
            announce.setDescription("No hay descripción");
        }
        Announce announceAux = announcementConsumer.getAnnouncementByURI(uri);
        announce.setDriver(announceAux.getDriver());
        announce.setPassengers(announceAux.getPassengers());
        announce.add(announceAux.getLinks());
        edit(announce);

        return "redirect:/details?announcementURI=" + uri;
    }
}
