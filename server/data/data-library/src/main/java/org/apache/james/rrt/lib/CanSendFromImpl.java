/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.rrt.lib;

import static org.apache.james.rrt.lib.Mapping.Type.Alias;
import static org.apache.james.rrt.lib.Mapping.Type.Domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.util.OptionalUtils;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;

public class CanSendFromImpl implements CanSendFrom {

    public static final EnumSet<Mapping.Type> ALIAS_TYPES_ACCEPTED_IN_FROM = EnumSet.of(Alias, Domain);
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    public CanSendFromImpl(RecipientRewriteTable recipientRewriteTable) {
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public boolean userCanSendFrom(Username connectedUser, Username fromUser) {
        try {
            return connectedUser.equals(fromUser) || emailIsAnAliasOfTheConnectedUser(connectedUser, fromUser);
        } catch (RecipientRewriteTableException | RecipientRewriteTable.ErrorMappingException e) {
            return false;
        }
    }

    @Override
    public Stream<MailAddress> allValidFromAddressesForUser(Username user) throws RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException {
        List<Domain> domains = relatedDomains(user).collect(Guavate.toImmutableList());

        return relatedAliases(user)
            .flatMap(allowedUser -> domains.stream()
                .map(Optional::of)
                .map(allowedUser::withOtherDomain)
                .map(Throwing.function(Username::asMailAddress).sneakyThrow()));
    }

    private boolean emailIsAnAliasOfTheConnectedUser(Username connectedUser, Username fromUser) throws RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException {
        return fromUser.getDomainPart().isPresent()
            && recipientRewriteTable.getResolvedMappings(fromUser.getLocalPart(), fromUser.getDomainPart().get(), ALIAS_TYPES_ACCEPTED_IN_FROM)
            .asStream()
            .map(Mapping::asMailAddress)
            .flatMap(OptionalUtils::toStream)
            .map(Username::fromMailAddress)
            .anyMatch(alias -> alias.equals(connectedUser));
    }

    private Stream<Username> relatedAliases(Username user) throws RecipientRewriteTableException {
        return Stream.concat(
            Stream.of(user),
            recipientRewriteTable
                .listSources(Mapping.alias(user.asString()))
                .map(MappingSource::asUsername)
                .flatMap(OptionalUtils::toStream)
        );
    }

    private Stream<Domain> relatedDomains(Username user) {
        return user.getDomainPart()
            .map(Throwing.function(this::fetchDomains).sneakyThrow())
            .orElseGet(Stream::empty);
    }

    private Stream<Domain> fetchDomains(Domain domain) throws RecipientRewriteTableException {
        return Stream.concat(
          Stream.of(domain),
          recipientRewriteTable
              .listSources(Mapping.domain(domain))
              .map(MappingSource::asDomain)
              .flatMap(OptionalUtils::toStream)
        );
    }
}
