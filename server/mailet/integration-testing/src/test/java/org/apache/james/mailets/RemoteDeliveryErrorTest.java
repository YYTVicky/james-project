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

package org.apache.james.mailets;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class RemoteDeliveryErrorTest {
    private static final String ANOTHER_DOMAIN = "other.com";

    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + ANOTHER_DOMAIN;
    private static final String ALWAYS_421_RCPT_BEHAVIOR = "[{" +
        "  \"condition\": {\"operator\":\"matchAll\"}," +
        "  \"response\": {\"code\":421, \"message\":\"mock response\", \"rejected\":true}," +
        "  \"command\": \"RCPT TO\"" +
        "}]";
    private static final String ALWAYS_421_FROM_BEHAVIOR = "[{" +
        "  \"condition\": {\"operator\":\"matchAll\"}," +
        "  \"response\": {\"code\":421, \"message\":\"mock response\", \"rejected\":true}," +
        "  \"command\": \"MAIL FROM\"" +
        "}]";
    private static final String ALWAYS_421_DATA_BEHAVIOR = "[{" +
        "  \"condition\": {\"operator\":\"matchAll\"}," +
        "  \"response\": {\"code\":421, \"message\":\"mock response\", \"rejected\":true}," +
        "  \"command\": \"DATA\"" +
        "}]";
    private static final String BOUNCE_MESSAGE = "Hi. This is the James mail server at localhost.\n" +
        "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
        "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
        "I include the list of recipients and the reason why I was unable to deliver\n" +
        "your message.";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @ClassRule
    public static DockerContainer mockSmtp = DockerContainer.fromName("chibenwa/mock-smtp-server")
        .withLogConsumer(outputFrame -> System.out.println(outputFrame.getUtf8String()));

    private TemporaryJamesServer jamesServer;

    @Before
    public void setUp() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(DEFAULT_DOMAIN, LOCALHOST_IP)
            .registerMxRecord(ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.simpleRoot())
                .putProcessor(CommonProcessors.error())
                .putProcessor(directResolutionTransport())
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
        with().delete("/smtpMails");
    }

    @Test
    public void remoteDeliveryShouldBounceWhenAlwaysRCPT421() throws Exception {
        given(requestSpecification())
            .body(ALWAYS_421_RCPT_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(BOUNCE_MESSAGE);
    }

    @Test
    public void remoteDeliveryShouldBounceWhenAlwaysFROM421() throws Exception {
        given(requestSpecification())
            .body(ALWAYS_421_FROM_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(BOUNCE_MESSAGE);
    }

    @Test
    public void remoteDeliveryShouldBounceWhenAlwaysDATA421() throws Exception {
        given(requestSpecification())
            .body(ALWAYS_421_DATA_BEHAVIOR)
            .put("/smtpBehaviors");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(BOUNCE_MESSAGE);
    }

    private ProcessorConfiguration.Builder directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.LOCAL_DELIVERY)
            .addMailet(MailetConfiguration.builder()
                .mailet(RemoteDelivery.class)
                .matcher(All.class)
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "10, 10, 10")
                .addProperty("maxRetries", "3")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "2")
                .addProperty("sendpartial", "true"));
    }

    private RequestSpecification requestSpecification() {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(8000)
            .setBaseUri("http://" + mockSmtp.getContainerIp())
            .build();
    }
}