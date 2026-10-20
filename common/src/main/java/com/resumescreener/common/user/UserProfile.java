package com.resumescreener.common.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private String userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String phone;
    private String headline;
    private String department;
    private String location;
    private String bio;

    @Column(nullable = false)
    private Instant updatedAt;

    private String updatedBy;

    public static UserProfile blankFor(User user) {
        UserProfile profile = new UserProfile();
        profile.user = user;
        return profile;
    }

    public void update(ProfileFields fields, String editorId, Instant at) {
        phone = fields.phone();
        headline = fields.headline();
        department = fields.department();
        location = fields.location();
        bio = fields.bio();
        updatedBy = editorId;
        updatedAt = at;
    }

    public ProfileFields fields() {
        return new ProfileFields(phone, headline, department, location, bio);
    }
}
