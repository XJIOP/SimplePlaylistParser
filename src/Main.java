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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Main {

	private boolean _debug = false;

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
		new Main().launch(args);
	}

	private void launch(String[] args) {
		log("launch");

		/* Read args */
		
		Options options = new Options();

	    Option input = new Option("i", "input", true, "input file path or url");
	    options.addOption(input);

	    Option output = new Option("o", "output", true, "output file");
	    options.addOption(output);

	    CommandLineParser parser = new DefaultParser();
	    HelpFormatter formatter = new HelpFormatter();
	    CommandLine cmd = null;

	    try {
	    	cmd = parser.parse(options, args);
	    } catch (ParseException e) {
	    	System.out.println(e.getMessage());
	        formatter.printHelp("utility-name", options);
	        System.exit(1);
	    }

	    String inputFile = cmd.getOptionValue("input");
	    String outputFile = cmd.getOptionValue("output");
	     
	    if(outputFile != null)
	    	_output = outputFile;
	     
	    /* Load settings */
		
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
			
		/* Run app */
		
		// cli
		if(inputFile != null) {
			
			if(inputFile.matches("^.*://")) {
				URL = inputFile;
				try {
					download();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			else
			{
				savePlaylist(readFile(inputFile));
			}
			
		}
		// service
		else
		{
			if(SERVER)
				Server();		
		
			Service();
		}
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
	
	private synchronized void Service() {
		log("run service");
		
		while (true) {
			try {
				download();				
				log("wait");
				this.wait(INTERVAL*60000);
				log("timeout");
			} catch (Exception e) {
				e.printStackTrace();
				break;
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
		String filter[] = read.split("\\r?\\n", -1);

		Map<String, String> channels = new LinkedHashMap<String, String>();
		
		String group = null;
		
		for(String f : filter) {

			Pattern p = Pattern.compile("\\[(.*)\\]");
			Matcher m = p.matcher(f);
			if (m.find()) {
				group = m.group(1);
			}
			
			channels.put(f, group);			
		}		
		
		StringBuffer result = new StringBuffer();
		result.append("#EXTM3U");
		result.append(System.lineSeparator());

		ArrayList<String> unique = new ArrayList<>();
		
		for (Map.Entry<String, String> ch : channels.entrySet()) {
			// log("channel | " + ch);
			Pattern p = Pattern.compile("(#EXTINF:[0-9- ]+(group-title=\"[^\"]*\")?.*,\\s*" + Pattern.quote(ch.getKey()) + ")\\s*(.*:\\/\\/.*)", Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(source);
			while (m.find()) {
				// log("found channel | " + ch);
				
				String link = m.group(3);
				
				if(unique.contains(link))
					continue;
				
				String extinf = m.group(1);
				
				if(ch.getValue() != null) {
				
					// add group
					if(m.group(2) == null) {
						extinf = extinf.replaceFirst("(#EXTINF:[0-9- ]+)", "$1 group-title=\"" + ch.getValue() + "\"");
					}
					// replace group
					else
					{
						extinf = extinf.replaceFirst("(group-title=\"[^\"]*\")", "group-title=\"" + ch.getValue() + "\"");
					}
				}
				
				unique.add(link);
				
				result.append(extinf);
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
