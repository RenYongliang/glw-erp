package com.gyw.backend.auth;

import com.gyw.backend.user.model.UserView;

public record AuthPrincipal(UserView user, String tokenId) {}
