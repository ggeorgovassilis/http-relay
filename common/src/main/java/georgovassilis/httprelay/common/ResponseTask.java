package georgovassilis.httprelay.common;

/**
 * Models an HTTP response which the web server responds with to the private relay
 * @author george georgovassilis
 *
 */

public class ResponseTask extends Task {

	/**
	 * HTTP status code
	 */
	protected int status;
	
	/**
	 * HTTP status message
	 */
	protected String statusMessage;

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}
}
