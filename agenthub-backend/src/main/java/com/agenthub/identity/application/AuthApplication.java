package com.agenthub.identity.application;

import com.agenthub.identity.dto.AuthTokenView;
import com.agenthub.identity.dto.LoginCommand;
import com.agenthub.identity.dto.RegisterCommand;
import com.agenthub.identity.dto.UserView;

public interface AuthApplication {
    AuthTokenView register(RegisterCommand cmd);
    AuthTokenView login(LoginCommand cmd);
    UserView getCurrentUser();
    UserView getUserById(String userId);
}
