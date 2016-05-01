package georgovassilis.httprelay.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Models an HTTP request which the browser submits to the public relay
 * @author george georgovassilis
 *
 */
public class RequestTask extends Task {

	/**
	 * HTTP methods like GET, POST etc
	 */
	protected String method;
	
	/**
	 * HTTP response to this request
	 */
	protected ResponseTask response;
	
	/**
	 * Requested URL. May or may not contain URL parameters; any parameters listed here
	 * may or may not repeat in the {@link #parameters} map
	 */
	protected String url;
	
	/**
	 * Map of parameters; these may (or may not) be parameters encoded in the URL (like http://example.com?param1=value1&param2=value2)
	 * or may be form parameters.
	 */
	protected Map<String, String> parameters = new HashMap<String, String>();

	public RequestTask() {
	}

	public RequestTask(String method, String url) {
		this.method = method;
		this.url = url;
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public void setParameters(Map<String, String> parameters) {
		this.parameters = parameters;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public ResponseTask getResponse() {
		return response;
	}

	public void setResponse(ResponseTask response) {
		this.response = response;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

}
