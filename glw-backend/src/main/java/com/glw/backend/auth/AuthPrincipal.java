package com.glw.backend.auth;

import com.glw.backend.user.model.UserView;

public record AuthPrincipal(UserView user, String tokenId) {}
