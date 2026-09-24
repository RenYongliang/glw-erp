package com.glw.backend.auth;

import com.glw.backend.model.entity.UserView;

public record AuthPrincipal(UserView user, String sessionId) {}
