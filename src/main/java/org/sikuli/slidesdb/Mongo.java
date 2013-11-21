package org.sikuli.slidesdb;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;


import org.bson.types.Binary;
import org.sikuli.slides.api.io.PPTXSlidesReader;
import org.sikuli.slides.api.io.SlidesReader;
import org.sikuli.slides.api.models.ImageElement;
import org.sikuli.slides.api.models.Slide;
import org.sikuli.slides.api.models.SlideElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.repackaged.com.google.common.base.Objects;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.IOUtils;
import com.google.api.client.util.Lists;
import com.google.api.client.util.Maps;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

public class Mongo {

	static Logger logger = LoggerFactory.getLogger(Mongo.class);

	private DB db;
	private DBCollection coll;

	Mongo(){
		MongoClient mongoClient;
		try {
			mongoClient = new MongoClient( "localhost" , 27017 );
			db = mongoClient.getDB("slidesdb");			
			coll = db.getCollection("phds");
		} catch (UnknownHostException e) {
			return;						
		}
	}


	private java.io.File repoDir = new java.io.File("repo");
	private java.io.File imageDir = new java.io.File(repoDir, "images");

	public java.io.File readFromRepo(String id, String title){		
		String filename = title.replace(" ","_") + "_" + id + ".pptx";
		java.io.File outFile = new java.io.File(repoDir, filename);
		return outFile;
	}

	public void saveToRepo(byte[] pptx, String id, String title){

		String filename = title.replace(" ","_") + "_" + id + ".pptx"; 

		java.io.File outFile = new java.io.File(repoDir, filename);

		FileOutputStream output;
		try {
			output = new FileOutputStream(outFile);
			output.write(pptx);
			output.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	   	
	}

	public void parseAll() throws IOException{
		DBCollection coll = db.getCollection("phds"); 
		DBCursor cursor = coll.find();
		try {			
			while(cursor.hasNext()) {

				DBObject doc = cursor.next();

				String id = (String) ((DBObject) doc.get("meta")).get("id");
				String title = (String) ((DBObject) doc.get("meta")).get("title");			   				   
				java.io.File file = readFromRepo(id, title);
				DBObject data = parsePPTX(id, file);

				BasicDBObjectBuilder set =  BasicDBObjectBuilder.start()
						.add("$set", BasicDBObjectBuilder.start()
								.add("data", data).get());

				BasicDBObjectBuilder q =  BasicDBObjectBuilder.start()
						.add("_id", doc.get("_id"));


				coll.update(q.get(),  set.get());
			}

		} finally {
			cursor.close();
		}
	}

	static String getImageFilename(String id, String field, int no){
		return String.format("%s-%s-%d.png", id, field, no);
	}


	public DBObject parsePPTX(String id, java.io.File file) throws IOException{
		SlidesReader reader = new PPTXSlidesReader();
		List<Slide> slides = reader.read(file);

		BasicDBObjectBuilder data = BasicDBObjectBuilder.start();
		BasicDBObjectBuilder milestone = BasicDBObjectBuilder.start();

		List<String> pubs = Lists.newArrayList();
		List<String> awards = Lists.newArrayList();
		List<DBObject> highlights = Lists.newArrayList();		

		for (Slide slide : slides){

			SlideElement headerElement = slide.select().hasText().orderByY().first();

			String key = headerElement.getText();
			//			System.out.print(key);

			slide.remove(headerElement);

			String hasImage;
			boolean exist = slide.select().isImage().exist();
			if (exist){
				hasImage = "[image] ";
			}else{
				hasImage = "";
			}

			List<SlideElement> list = slide.select().hasText().all();
			String joinedText = "";
			for (SlideElement e : list){
				joinedText = joinedText + " " + e.getText();
			}
			joinedText = joinedText.replaceAll("\n", " ");

			//			System.out.println(" ==> " + hasImage + joinedText );

			String value = joinedText.trim();

			if (value.startsWith("{") && value.endsWith("}")){
				continue;
			}

			if (key.equalsIgnoreCase("name")){
				data.add("name", value);
			}else if (key.equalsIgnoreCase("email")){
				data.add("email", value);
			}else if (key.equalsIgnoreCase("url")){
				data.add("url", value);				
			}else if (key.equalsIgnoreCase("advisor")){
				data.add("advisor", value);		
			}else if (key.equalsIgnoreCase("Current Funding")){				
				data.add("funding", value);
			}else if (key.equalsIgnoreCase("Committee Members")){				
				data.add("committee", value);
			}else if (key.equalsIgnoreCase("starting semester/year")){
				data.add("start", value);
			}else if (key.equalsIgnoreCase("Next Milestone")){
				milestone.add("milestone", value);
			}else if (key.equalsIgnoreCase("Next Milestone Target Time")){
				milestone.add("time", value);
			}else if (key.equalsIgnoreCase("publication")){				
				pubs.add(value);
			}else if (key.equalsIgnoreCase("photo")){

				// TODO: export images as ID-photo-1.png
				List<DBObject> images = exportSlideImages(slide, id, "photo");

				data.add("photo", images);

			}else if (key.equalsIgnoreCase("award")){				
				awards.add(value);
			}else if (key.toLowerCase().startsWith("highlight")){				


				BasicDBObjectBuilder highlight = BasicDBObjectBuilder.start();				
				highlight.add("caption", value);

				int j = highlights.size();
				List<DBObject> images = exportSlideImages(slide, id, "highlight"+j);
				
				highlight.add("images", images);				

				highlights.add(highlight.get());				
			}

		}

		if (!milestone.isEmpty())
			data.add("next", milestone.get());
		if (pubs.size()>0)
			data.add("pubs", pubs);
		if (awards.size()>0)
			data.add("awards", awards);
		if (highlights.size()>0)
			data.add("highlights", highlights);				

		//		BasicDBObjectBuilder o =  BasicDBObjectBuilder.start()	   		
		//			.add("data", data.get());

		logger.trace(data.get().toString());

		return data.get();
	}

	private List<DBObject> exportSlideImages(Slide slide, String id, String field)
			throws IOException {
		List<SlideElement> elements = slide.select().isImage().all();

		List<DBObject> images = Lists.newArrayList();				
		for (int i = 0; i < elements.size(); ++i){
			BasicDBObjectBuilder image = BasicDBObjectBuilder.start();

			SlideElement element = elements.get(i);					
			ImageElement imageElement = (ImageElement) element;

			String srcFilename = imageElement.getFileName();
			BufferedImage sourceImage=ImageIO.read(new java.io.File(srcFilename));

			String destFilename = getImageFilename(id, field, i);					
			java.io.File dest = new java.io.File(imageDir, destFilename);

			//					ByteArrayOutputStream baos=new ByteArrayOutputStream();
			ImageIO.write(sourceImage, "png", dest);
			//					byte[] imageInByte=baos.toByteArray();

			image.add("h",  sourceImage.getHeight());
			image.add("w",  sourceImage.getWidth());
			image.add("path", destFilename);

			images.add(image.get());
		}
		return images;
	}

	public DBObject parsePPTX(String id, byte[] fileBytes) throws IOException{
		///		byte[] fileBytes = (byte[]) pptx.get("pptx");


		java.io.File temp = java.io.File.createTempFile("temp",".txt");
		temp.deleteOnExit();


		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temp));
		bos.write(fileBytes);
		bos.flush();
		bos.close();

		return parsePPTX(id, temp);	
	}



	public boolean exist(String id){
		BasicDBObject q = new BasicDBObject("meta.id", id); 
		DBObject r = coll.findOne(q);
		return r != null;
	}

	private static byte[] readImageAsBytes(java.io.File file) throws IOException{
		BufferedImage originalImage=ImageIO.read(file);
		ByteArrayOutputStream baos=new ByteArrayOutputStream();
		ImageIO.write(originalImage, "png", baos );
		byte[] imageInByte=baos.toByteArray();
		return imageInByte;
	}

	private static byte[] downloadAsPPTXAsBytes(Drive service, File file) {
		logger.trace("downloading the remote file as pptx");

		InputStream is = downloadAsPPTX(service, file);
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int nRead;
		byte[] data = new byte[16384];
		try {
			while ((nRead = is.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, nRead);
			}
			buffer.flush();
			return buffer.toByteArray();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	private static InputStream downloadAsPPTX(Drive service, File file) {
		Map<String, String> exportLinks = file.getExportLinks();
		String pptxExportLink = exportLinks.get("application/vnd.openxmlformats-officedocument.presentationml.presentation");

		if (pptxExportLink != null && pptxExportLink.length() > 0) {
			try {
				HttpResponse resp =
						service.getRequestFactory().buildGetRequest(new GenericUrl(pptxExportLink))
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

	public void pull(File gdFile, Drive drive){		

		logger.trace("pulling remote file id = " + gdFile.getId());

		DBObject q = new BasicDBObject("meta.id", gdFile.getId()); 

		DateTime newPulledDate = new DateTime(new Date());

		DBObject gfile = BasicDBObjectBuilder.start()
				.add("id", gdFile.getId())
				.add("ownerNames",gdFile.getOwnerNames())
				.add("lastModifyingUserName", gdFile.getLastModifyingUserName())
				.add("modifiedDate" , gdFile.getModifiedDate().toStringRfc3339())
				.add("defaultOpenWithLink", gdFile.getDefaultOpenWithLink())
				.add("title", gdFile.getTitle())
				.get();

		BasicDBObjectBuilder doc = BasicDBObjectBuilder.start()
				.add("pulledDate", newPulledDate.toStringRfc3339())
				.add("meta", gfile);

		String title = gdFile.getTitle();
		String id = gdFile.getId();


		DBObject r = coll.findOne(q);		

		if (r != null){
			logger.trace("the remote file has been pulled previously");

			doc.add("_id", r.get("_id"));		

			String pulledDateString = (String) r.get("pulledDate");
			DateTime lastPulledDate = DateTime.parseRfc3339(pulledDateString);
			DateTime modifiedDate = gdFile.getModifiedDate();

			if (lastPulledDate.getValue() < modifiedDate.getValue()){
				logger.trace("the remote file has been modified since last pull");

				byte[] bytes = downloadAsPPTXAsBytes(drive, gdFile);
				saveToRepo(bytes, id, title);

				try {
					DBObject data = parsePPTX(id, bytes);
					doc.add("data", data);				
				} catch (IOException e) {				
					e.printStackTrace();
				}


			}else{
				logger.trace("the remote file has no modification");

				//saveToRepo(pptx)
				//doc.add("pptx", r.get("pptx"));
				doc.add("data", r.get("data"));			
			}

		}else{
			logger.trace("the remote file is being pulled the first time");

			byte[] bytes = downloadAsPPTXAsBytes(drive, gdFile);
			saveToRepo(bytes, id, title);

			try {
				DBObject data = parsePPTX(id, bytes);
				doc.add("data", data);				
			} catch (IOException e) {				
				e.printStackTrace();
			}		   	

		}


		coll.save(doc.get());		
		logger.trace("pulling is complete");		
	}

	public static void main(String[] args) throws IOException {



		Mongo db = new Mongo();
		db.exportYAML();
//				db.parseAll();

		//mongoClient.c


	}

	private void exportYAML() throws FileNotFoundException {

		PrintStream out = new PrintStream(new FileOutputStream(new java.io.File("../gradcomm/phds/_data/people.yaml")));
//		PrintStream out = System.out;
		
		List<Map<String, Object> > list = Lists.newArrayList(); 

		DBCollection coll = db.getCollection("phds");
		DBCursor cursor = coll.find();
				
		try {			
			while(cursor.hasNext()) {
				
				Map<String, Object> m = Maps.newHashMap();

				DBObject item = cursor.next();
				DBObject data = (DBObject) item.get("data");
				DBObject meta = (DBObject) item.get("meta");
				
				
				String user = (String) meta.get("lastModifyingUserName");
				if (user.toLowerCase().startsWith("jacqueline")){					
					m.put("updated", "no");
				}else{
					m.put("updated", "yes");
				}
				
				m.put("name", (String) data.get("name"));
				m.put("id", (String) meta.get("id"));
				m.put("email", (String) data.get("email"));
				m.put("advisor", (String) data.get("advisor"));
				m.put("committee", (String) data.get("committee"));
				m.put("funding", (String) data.get("funding"));
				m.put("start", (String) data.get("start"));
				
				
				DBObject next = (DBObject) data.get("next");
				String milestone = null;
				String time = null;
				if (next != null){					
					milestone =  (String) next.get("milestone");
					time = (String) next.get("time");
				}

				Map<String, String> n = Maps.newHashMap();
				n.put("milestone",  Objects.firstNonNull(milestone, "n/a"));
				n.put("time", Objects.firstNonNull(time, "n/a"));				
				m.put("next", n);
				
				

				

				BasicDBList pubs = (BasicDBList) data.get("pubs");
				m.put("pubs", pubs);
				
				if (pubs != null){
					m.put("pubscount", pubs.size());
				}else{
					m.put("pubscount", 0);
				}
				
				BasicDBList awards = (BasicDBList) data.get("awards");
				m.put("awards", awards);
				
				if (awards != null){
					m.put("awardscount", awards.size());
				}else{
					m.put("awardscount", 0);
				}
				
				BasicDBList highlights = (BasicDBList) data.get("highlights");				
				if (highlights != null){
					m.put("highlights", highlights);
					m.put("highlightscount", highlights.size());
				}else{
					m.put("highlights", Lists.newArrayList());
					m.put("highlightscount", 0);
				}
				
				//				String id = (String) ((DBObject) item.get("meta")).get("id");
				BasicDBList filename = (BasicDBList) data.get("photo");;
				if (filename != null && filename.size() > 0){
					m.put("photo", ((DBObject)(filename.get(0))).get("path"));	
				}
				
				
				
				//			   	String id = (String) ((DBObject) item.get("meta")).get("id");
				//			   	String title = (String) ((DBObject) item.get("meta")).get("title");


				//			   	saveToRepo(pptx, id, title)
				//			   	DBObject data = parsePPTX(pptx);
				//			   	DBObject set = BasicDBObjectBuilder.start()
				//			   			.add("$set", data).get();
				//			   	
				//			   	coll.update(item, set);
				
				list.add(m);
			}
		} finally {
			cursor.close();
		}		
		
		
		// sort by last name
		Collections.sort(list, new Comparator< Map<String, Object>>(){
			public int compare(Map<String, Object> a,
					Map<String, Object> b) {
				String name1 = (String) a.get("name");
				String name2 = (String) b.get("name");
				String[] toks1 = name1.split(" ");
				String[] toks2 = name2.split(" ");
				return toks1[toks1.length-1].compareToIgnoreCase(toks2[toks2.length-1]);
			}			
		});		
		
		Yaml yaml = new Yaml();
//		String output = yaml.dump(list.subList(0, 20));
		String output = yaml.dump(list);
		out.println(output);
		out.close();
	}
}
