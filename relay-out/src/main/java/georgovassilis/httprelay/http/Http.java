package georgovassilis.httprelay.http;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

/**
 * Implementation-agnostic API for HTTP requests
 * @author george georgovassilis
 *
 */
public interface Http {

	/**
	 * Execute asynchronously an HTTP request descriped by {@link RequestTask}
	 * and return a handle to the response.
	 * @param request
	 * @return
	 * @throws IOException
	 */
	CompletableFuture<ResponseTask> execute(RequestTask request) throws IOException;
	
}
