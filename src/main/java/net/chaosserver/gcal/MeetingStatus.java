package net.chaosserver.gcal;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Scanner;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.Events;

/**
 * Call to check for the current meeting status of a Google Calendar.
 * 
 * The first time this is run it's important to run in interactive mode (-i). It
 * will trigger an OAuth against Google Calendar and the -i will allow you to
 * enter the code requested by Google Calendar. This gets stored into
 * 
 * <pre>
 * .store/calendar_data
 * </pre>
 * 
 * @author jreed
 */
public class MeetingStatus {
    @Parameter(names = { "-i", "--interactive" }, description = "Runs in interactive mode.")
    protected boolean isInteractive = false;

    @Parameter(names = { "-u", "--user-name" }, description = "The user name.")
    protected String username = System.getenv("googleCalendarUsername");

    @Parameter(names = { "-c", "--client-id" }, description = "The client id.")
    protected String clientId = "950443281089-hmckuorbl35qc6kr3nt7rfj5rmt80c6e.apps.googleusercontent.com"; // "foo@gmail.com"

    @Parameter(names = { "-s", "--client-secret" }, description = "The client secret.")
    protected String clientSecret = System.getenv("googleCalendarSecret");

    public static void main(String args[]) throws GeneralSecurityException,
            IOException {

        MeetingStatus meetingStatus = new MeetingStatus();
        new JCommander(meetingStatus, args);
        meetingStatus.getEntries();

    }

    public void getEntries() throws GeneralSecurityException, IOException {
        java.io.File DATA_STORE_DIR = new java.io.File(
                System.getProperty("user.home"), ".store/calendar_data");

        HttpTransport httpTransport = GoogleNetHttpTransport
                .newTrustedTransport();

        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(
                DATA_STORE_DIR);

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
        GoogleClientSecrets.Details clientSecretDetails = new GoogleClientSecrets.Details();
        clientSecretDetails.setClientId(clientId);
        clientSecretDetails.setClientSecret(clientSecret);
        clientSecrets.setInstalled(clientSecretDetails);

        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JacksonFactory.getDefaultInstance(),
                clientSecrets,
                Collections.singleton(CalendarScopes.CALENDAR_READONLY))
                .setDataStoreFactory(dataStoreFactory).build();

        VerificationCodeReceiver verificationCodeReceiver = new MeetingStatus.LocalCodeReceiver();

        Credential credential = new AuthorizationCodeInstalledApp(flow,
                verificationCodeReceiver).authorize("user");

        Calendar client = new com.google.api.services.calendar.Calendar.Builder(
                httpTransport, JacksonFactory.getDefaultInstance(),
                credential).setApplicationName("calendar-status").build();

        // new DateTime()
        java.util.Calendar now = java.util.Calendar.getInstance();
        DateTime nowDateTime = new DateTime(now.getTime());
        now.add(java.util.Calendar.MINUTE, 1);
        DateTime soonDateTime = new DateTime(now.getTime());

        Events feed = client
                .events()
                .list(username)
                .setShowDeleted(Boolean.FALSE)
                .setTimeMin(nowDateTime)
                .setTimeMax(soonDateTime)
                .setFields(
                        "items(attendees(email,id,responseStatus),summary,transparency)")
                .execute();

        boolean complete = false;
        if (feed.getItems() != null) {
            for (Event entry : feed.getItems()) {
                if (!complete) {
                    String transparency = entry.getTransparency();
                    if (!"transparent".equals(transparency)) {
                        for (EventAttendee attendee : entry.getAttendees()) {
                            if (username.equals(attendee.getEmail())
                                    && "accepted".equals(attendee
                                            .getResponseStatus())) {

                                System.out.println(entry.getSummary());
                                complete = true;
                            }
                        }
                    }
                }
            }
        }
        if (!complete) {
            System.out.println("none");
        }

    }

    public class LocalCodeReceiver implements VerificationCodeReceiver {
        @Override
        public String getRedirectUri() throws IOException {
            return "urn:ietf:wg:oauth:2.0:oob";
        }

        @Override
        public void stop() throws IOException {
        }

        @Override
        public String waitForCode() throws IOException {
            if (isInteractive) {
                System.out.println("Enter your code: ");
                Scanner in = new Scanner(System.in);
                String code = in.nextLine();
                return code;
            } else {
                System.out.println("oauth");
                System.exit(1);
                return "";
            }
        }
    }
}
