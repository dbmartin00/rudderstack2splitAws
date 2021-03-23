package io.split.dbm.integrations.rudderstack2split;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.gson.Gson;

public class LambdaFunctionHandler implements RequestStreamHandler {

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {

        LambdaLogger logger = context.getLogger();
		String json = IOUtils.toString(input, Charset.forName("UTF-8"));
		JSONObject requestObject = new JSONObject(json);
		long start = System.currentTimeMillis();
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

		Map<String, String> headers = new TreeMap<String, String>();
		JSONObject headersObj = requestObject.getJSONObject("headers");
		for(String key : headersObj.keySet()) {
			headers.put(key.toLowerCase(), headersObj.getString(key));
		}
		
		String splitApiKey = headers.get("splitapikey");
		String trafficType = headers.get("traffictype");
		String environmentName = headers.get("environmentname");

		System.out.println("DEBUG - " + splitApiKey + " - " + trafficType + " - " + environmentName);
		
		JSONObject iObj = requestObject.getJSONObject("body");	
		//System.out.println(iObj.toString(2));

		RudderstackEvent rEvent = new Gson().fromJson(iObj.toString(), RudderstackEvent.class);
		System.out.println("created RudderstackEvent - " + rEvent.anonymousId + " - " + rEvent.event);

		JSONArray splitEvents = new JSONArray();
		String key = null;
		if(rEvent.userId != null && !rEvent.userId.isEmpty()) {
			key = rEvent.userId;
		} else if (rEvent.anonymousId != null && !rEvent.anonymousId.isEmpty()) {
			key = rEvent.anonymousId;
		} else {
			System.out.println("no key found in anonymous or user id");
		}
		if(key != null) {
			JSONObject splitEvent = new JSONObject();
			splitEvent.put("key", key);
			splitEvent.put("trafficTypeName", trafficType);
			String eventTypeId = cleanEventTypeId(rEvent.event);
			if(eventTypeId.isEmpty()) {
				eventTypeId = rEvent.type;
			}
			splitEvent.put("eventTypeId", eventTypeId);
			splitEvent.put("value", 0);
			splitEvent.put("environmentName", environmentName);
			
			String timestamp = rEvent.originalTimestamp;
			try {
				splitEvent.put("timestamp", sdf.parse(timestamp).getTime());
			} catch (Exception e) {
				logger.log("error parsing timestamp: " + timestamp + " + - " + e.getMessage());
			}
			
			Map<String, Object> properties = new TreeMap<String, Object>();
			if(iObj.has("properties")) {
				putProperties(properties, "", iObj.getJSONObject("properties"));
			}
			if(iObj.has("context")) {
				putProperties(properties, "context.", iObj.getJSONObject("context"));
			}	
			properties.put("channel", rEvent.channel);
			properties.put("receivedAt", rEvent.receivedAt);
			properties.put("sentAt", rEvent.sentAt);
			properties.put("rudderId", rEvent.rudderId);
			properties.put("type", rEvent.type);
			
			splitEvent.put("properties", properties);
			splitEvents.put(splitEvent);
		}

		CreateEvents creator = new CreateEvents(splitApiKey, 100); // rudderstack sends one at time
		try {
			creator.doPost(splitEvents);
		} catch (Exception e) {
			logger.log(("error sending events to Split: " + e.getMessage()));
		}

		System.out.println("finished in " + (System.currentTimeMillis() - start) + "ms");
	}

	private String cleanEventTypeId(String eventName) {
		String result = "";
		if(eventName != null) {
			char letter;
			for(int i = 0; i < eventName.length(); i++) {
				letter = eventName.charAt(i);
				if(!Character.isAlphabetic(letter)
						&& !Character.isDigit(letter)) {
					if(i == 0) {
						letter = '0';
					} else {
						if (letter != '-' && letter != '_' && letter != '.') {
							letter = '_';
						}
					}
				}
				result += "" + letter;
			}
		}
		return result;
	}
	
	private void putProperties(Map<String, Object> properties, String prefix, JSONObject obj) {
		for(String k : obj.keySet()) {
			if(obj.get(k) instanceof JSONArray) {
				JSONArray array = obj.getJSONArray(k);
				for(int j = 0; j < array.length(); j++) {
					putProperties(properties, prefix + k + ".", array.getJSONObject(j));
				}
			} else if (obj.get(k) instanceof JSONObject) {
				JSONObject o = obj.getJSONObject(k);
				for(String key : o.keySet()) {
					if(o.get(key) instanceof JSONObject) {
						JSONObject d = (JSONObject) o.get(key);
						putProperties(properties, prefix + key + ".", d);
					} else {
						properties.put(prefix + k + "." + key, o.get(key));
					}
				}
			} else {
				properties.put(prefix + k, obj.get(k));
			}
		}
	}
}
