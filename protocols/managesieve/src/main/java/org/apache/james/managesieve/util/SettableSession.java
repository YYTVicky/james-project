/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.util;

import org.apache.james.core.Username;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.commands.Authenticate;

public class SettableSession implements Session {

    private Username user;
    private State state;
    private Authenticate.SupportedMechanism choosedAuthenticationMechanism;
    private boolean sslEnabled;

    public SettableSession() {
        this.state = State.UNAUTHENTICATED;
        this.sslEnabled = false;
    }

    @Override
    public Username getUser() {
        return user;
    }

    @Override
    public boolean isAuthenticated() {
        return state == State.AUTHENTICATED;
    }

    @Override
    public void setUser(Username user) {
        this.user = user;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State state) {
        this.state = state;
    }

    @Override
    public Authenticate.SupportedMechanism getChoosedAuthenticationMechanism() {
        return choosedAuthenticationMechanism;
    }

    @Override
    public void setChoosedAuthenticationMechanism(Authenticate.SupportedMechanism choosedAuthenticationMechanism) {
        this.choosedAuthenticationMechanism = choosedAuthenticationMechanism;
    }

    @Override
    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    @Override
    public boolean isSslEnabled() {
        return sslEnabled;
    }
}
