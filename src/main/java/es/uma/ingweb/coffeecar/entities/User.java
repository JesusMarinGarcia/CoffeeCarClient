package es.uma.ingweb.coffeecar.entities;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private long id;
    private String name;
    private String email;
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Announce> ownedAnnounces;
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Announce> joinedAnnounces;

    private String selfURI;
    private String ownedAnnouncementsURI;
    private String joinedAnnouncementsURI;
}
