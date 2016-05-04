package georgovassilis.httprelay.in;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

public class TaskGroup {

	protected RequestTask requestTask;
	protected ResponseTask responseTask;
	protected RequestCallback requestCallback;

	public RequestTask getRequestTask() {
		return requestTask;
	}

	public void setRequestTask(RequestTask requestTask) {
		this.requestTask = requestTask;
	}

	public ResponseTask getResponseTask() {
		return responseTask;
	}

	public void setResponseTask(ResponseTask responseTask) {
		this.responseTask = responseTask;
	}

	public RequestCallback getRequestCallback() {
		return requestCallback;
	}

	public void setRequestCallback(RequestCallback requestCallback) {
		this.requestCallback = requestCallback;
	}
}
