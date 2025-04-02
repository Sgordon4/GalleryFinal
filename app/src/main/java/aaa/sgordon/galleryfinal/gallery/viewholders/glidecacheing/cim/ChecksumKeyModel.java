package aaa.sgordon.galleryfinal.gallery.viewholders.glidecacheing.cim;

public class ChecksumKeyModel {
	private final String cacheKey;
	private final String uri;

	public ChecksumKeyModel(String cacheKey, String uri) {
		this.cacheKey = cacheKey;
		this.uri = uri;
	}

	public String getCacheKey() {
		return cacheKey;
	}

	public String getUri() {
		return uri;
	}
}
