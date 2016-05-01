package georgovassilis.httprelay.common;

import java.util.HashMap;
import java.util.Map;
 
/**
 * Base class for a a network task such as an HTTP request or response.
 * @author george georgovassilis
 *
 */
public abstract class Task {

	/**
	 * Unique identifier of a task.
	 */
	protected String id;

	/**
	 * HTTP headers
	 */
	protected Map<String, String> headers = new HashMap<String, String>();
	
	/**
	 * Any content such as the body of a POST request or the content returned for an HTTP response
	 */
	protected byte[] content;
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] content) {
		this.content = content;
	}

}
