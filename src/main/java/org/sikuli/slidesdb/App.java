package org.sikuli.slidesdb;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.IOUtils;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Children.List;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.Drive.Children;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.User;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Map;


public class App {

	private static String CLIENT_ID = "454070001197.apps.googleusercontent.com";
	private static String CLIENT_SECRET = "Q6WzCbW7XjU6Vgw7LcU1AqsW";

	private static String REFRESH_TOKEN = "1/L0BQ-gNMi-uaH21A-1oB6ggY93WapzGgqhUYiYLN7WY";

	private static String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";

	public static void main(String[] args) throws IOException {
		HttpTransport httpTransport = new NetHttpTransport();
		JsonFactory jsonFactory = new JacksonFactory();

		//    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
		//        httpTransport, jsonFactory, CLIENT_ID, CLIENT_SECRET, Arrays.asList(DriveScopes.DRIVE))
		//        .setAccessType("offline")
		//        .setApprovalPrompt("auto").build();
		//    
		//    String url = flow.newAuthorizationUrl().setRedirectUri(REDIRECT_URI).build();
		//    System.out.println("Please open the following URL in your browser then type the authorization code:");
		//    System.out.println("  " + url);
		//    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		//    String code = br.readLine();
		//    
		//    GoogleTokenResponse response = flow.newTokenRequest(code).setRedirectUri(REDIRECT_URI).execute();
		//    //response.getAccessToken();
		//    //response.get
		//    response.getAccessToken();
		//    System.out.println(response.getRefreshToken());
		GoogleCredential credential = new GoogleCredential.Builder()
		.setClientSecrets(CLIENT_ID, CLIENT_SECRET)
		.setTransport(httpTransport)
		.setJsonFactory(jsonFactory).build();

		//    	.setRe
		//    	.setre;

		credential.setRefreshToken(REFRESH_TOKEN);
		//    .setFromTokenResponse(response);

		System.out.println(credential.getRefreshToken());
		//Create a new authorized API client
		Drive service = new Drive.Builder(httpTransport, jsonFactory, credential)
		.setApplicationName("SlidesDB/1.0")
		.build();

		Mongo db = new Mongo();


		Children.List request = service.children()
				.list("0B13qkcUv_Ee7NWJaVjZNTkRpd3c")
				.setMaxResults(20);
		    do {
		try {
			ChildList children = request.execute();

			for (ChildReference child : children.getItems()) {
				//            System.out.println("File Id: " + child.getId());

				String fileId = child.getId();
				File file = service.files().get(fileId).execute();
				User user = file.getLastModifyingUser();

				System.out.print("title: " + file.getTitle());
				System.out.println(",    last modified by: " + user.getDisplayName());
				file.getModifiedDate();

//				byte[] bytes = downloadAsPPTXAsBytes(service, file);
				db.pull(file, service);

			}
			request.setPageToken(children.getNextPageToken());
		} catch (IOException e) {
			System.out.println("An error occurred: " + e);
			request.setPageToken(null);
		}
		      } while (request.getPageToken() != null &&
		               request.getPageToken().length() > 0);

		//    //Insert a file  
		//    File body = new File();
		//    body.setTitle("My document");
		//    body.setDescription("A test document");
		//    body.setMimeType("text/plain");
		//    
		//    java.io.File fileContent = new java.io.File("document.txt");
		//    FileContent mediaContent = new FileContent("text/plain", fileContent);



		//    File file = service.files().insert(body, mediaContent).execute();
		//    System.out.println("File ID: " + file.getId());
	}


	
	

	
	

	/**
	 * Download a file's content.
	 *
	 * @param service Drive API service instance.
	 * @param file Drive File instance.
	 * @return InputStream containing the file's content if successful,
	 *         {@code null} otherwise.
	 */
	private static InputStream downloadFile(Drive service, File file) {
		if (file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {
			try {
				HttpResponse resp =
						service.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()))
						.execute();
				return resp.getContent();
			} catch (IOException e) {
				// An error occurred.
				e.printStackTrace();
				return null;
			}
		} else {
			// The file doesn't have any content stored on Drive.
			return null;
		}
	}
}