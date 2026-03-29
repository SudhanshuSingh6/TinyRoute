package com.tinyroute.security;

import com.tinyroute.entity.Role;
import com.tinyroute.entity.User;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "password")

public class UserDetailsImpl implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String username, String email, String password,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    public static UserDetailsImpl build(User user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Role role = user.getRole();

        authorities.add(new SimpleGrantedAuthority(role.name()));

        if (role == Role.ROLE_PREMIUM) {
            authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER.name()));
        } else if (role == Role.ROLE_ADMIN) {
            authorities.add(new SimpleGrantedAuthority(Role.ROLE_PREMIUM.name()));
            authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER.name()));
        }

        return new UserDetailsImpl(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                authorities
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}