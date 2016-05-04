package georgovassilis.httprelay.in;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

public interface RequestCallback {

	void onResponseReceived(ResponseTask response);
	
	void onError(Exception e);
}
