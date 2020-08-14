public class Dummy {

	public static class Channel implements Comparable<Channel> {

        public final String name;
        public final String url;
        public final String data;
		
        public Channel(String name,
        		String url,
                String data) {
        	this.name = name;
        	this.url = url;
        	this.data = data;
        }        
        
		@Override
		public int compareTo(Channel arg0) {
			return this.name.compareTo(arg0.name);
		}		
	}
}
