public class Dummy {

	public static class Channel implements Comparable<Channel> {

        public final String name;
        public final String data;
		
        public Channel(String name,
                String data) {
        	this.name = name;
        	this.data = data;
        }        
        
		@Override
		public int compareTo(Channel arg0) {
			return this.name.compareTo(arg0.name);
		}		
	}
}
