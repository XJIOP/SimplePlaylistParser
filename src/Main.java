import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Main {

	private boolean _debug = true;

	private String SETTINGS_FILE = "config/settings.cfg";
	private String FILTER_FILE = "config/filter.list";

	private String URL;
	private String CHARSET = "UTF8";
	private boolean FILTER;
	private int INTERVAL;
	private boolean SERVER;
	private int PORT;

	private long _timestamp;
	private String _output = "playlist.m3u";
	private static String _playlist;
	
	public static void main(String[] args) {		
		new Main().launch();
	}

	private void launch() {
		log("launch");

		String config = readFile(SETTINGS_FILE);

		Properties prop = new Properties();
		try {
			prop.load(new StringReader(config));
		} catch (Exception e) {
			e.printStackTrace();
		}

		URL = prop.getProperty("URL");
		CHARSET = prop.getProperty("CHARSET");
		FILTER = prop.getProperty("FILTER").equals("true");
		INTERVAL = Integer.parseInt(prop.getProperty("INTERVAL"));		
		SERVER = prop.getProperty("SERVER").equals("true");
		PORT = Integer.parseInt(prop.getProperty("PORT"));
			
		if(SERVER)
			Server();		
		
		Parser();
	}

	private void Server() {
		log("run server");
		
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
	        server.createContext("/"+_output, new ServerHandler());
	        server.setExecutor(null);
	        server.start();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
    static class ServerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = _playlist != null && !_playlist.isEmpty() ? _playlist : "Empty playlist";
            t.getResponseHeaders().set("Content-Type", "audio/x-mpegurl");
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
	
	private synchronized void Parser() {
		log("run parser");
		
		while (true) {
			try {
				download();
				log("wait");
				this.wait(INTERVAL*60000);
				log("timeout");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void download() throws Exception {
		log("download");

		URL url = new URL(URL);

		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestProperty("Accept-Encoding", "gzip");

		if (con.getResponseCode() != 200) {
			log("httpcode " + con.getResponseCode());
			return;
		}

		long lastModified = con.getLastModified();
		if (lastModified == _timestamp) {
			log("no changes");
			return;
		}

		_timestamp = lastModified;

		BufferedReader in = null;
		if ("gzip".equals(con.getContentEncoding())) {
			in = new BufferedReader(new InputStreamReader(new GZIPInputStream(con.getInputStream()), CHARSET));
			log("gzip");
		} else {
			in = new BufferedReader(new InputStreamReader(con.getInputStream(), CHARSET));
			log("no gzip");
		}

		StringBuffer output = new StringBuffer();
		String inputLine;

		while ((inputLine = in.readLine()) != null) {
			output.append(inputLine);
			output.append(System.lineSeparator());
		}

		in.close();

		savePlaylist(output.toString());
	}

	private void savePlaylist(String source) {
		log("savePlaylist");

		String result;

		if (FILTER)
			result = filterChannels(source);
		else
			result = source;

		_playlist = result;
		
		writeToFile(_output, result);
	}

	private String filterChannels(String source) {
		log("filterChannels");

		String read = readFile(FILTER_FILE);
		String channels[] = read.split("\\r?\\n", -1);
		
		StringBuffer result = new StringBuffer();
		result.append("#EXTM3U");
		result.append(System.lineSeparator());

		ArrayList<String> unique = new ArrayList<>();
		
		for (String ch : channels) {
			// log("channel | " + ch);
			Pattern p = Pattern.compile("(#EXTINF:.*,\\s*" + Pattern.quote(ch) + ")\\s*(.*:\\/\\/.*)");
			Matcher m = p.matcher(source);
			while (m.find()) {
				// log("found channel | " + ch);
				
				String link = m.group(2);
				
				if(unique.contains(link))
					continue;
				
				unique.add(link);
				
				result.append(m.group(1));
				result.append(System.lineSeparator());
				result.append(link);
				result.append(System.lineSeparator());
			}
		}

		return result.toString();
	}

	private String readFile(String location) {
		log("readFile | " + location);

		String result = "";
		FileInputStream input;

		try {

			input = new FileInputStream(new File(location));
			InputStreamReader reader = new InputStreamReader(input, CHARSET);
			BufferedReader bufferedReader = new BufferedReader(reader);

			StringBuilder sb = new StringBuilder();
			String line = bufferedReader.readLine();

			while (line != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
				line = bufferedReader.readLine();
			}

			bufferedReader.close();
			result = sb.toString();

		} catch (Exception e) {
			e.printStackTrace();
		}

		return result.trim();
	}

	private void writeToFile(String location, String text) {
		log("writeToFile | " + location);

		try {
			
			if(!CHARSET.matches("(UTF-?8)"))
				text = new String(text.getBytes(), "UTF-8");

			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(location), CHARSET));
			
			bufferedWriter.write(text.trim());
			bufferedWriter.newLine();
			bufferedWriter.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void log(String text) {
		if (_debug) {
			System.out.println(text);
		}
	}
}
