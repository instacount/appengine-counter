package io.instacount.appengine.counter.ext;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Preconditions;
import io.instacount.appengine.counter.service.ShardedCounterService;
import io.instacount.appengine.counter.service.ShardedCounterServiceImpl;

/**
 * An extension of {@link HttpServlet} that provides a default implementation to handle counter deletion via the
 * Appengine task queue infrastructure. Note that this class must be wired into your web framework before it can be
 * usable.
 */
public class DefaultDeletionTaskHandler extends HttpServlet
{
	private final Logger logger = Logger.getLogger(this.getClass().getName());
	private ShardedCounterService shardedCounterService;

	/**
	 * No-args constructor for dependency-injection frameworks that easily can't handle constructor in a Servlet.
	 */
	public DefaultDeletionTaskHandler()
	{
	}

	/**
	 * Required-args Constructor.
	 * 
	 * @param shardedCounterService An instance of {@link ShardedCounterService} for processing actual delete
	 *            operations.
	 */
	public DefaultDeletionTaskHandler(final ShardedCounterService shardedCounterService)
	{
		Preconditions.checkNotNull(shardedCounterService);
		this.shardedCounterService = shardedCounterService;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
	{
		final String counterName = req.getParameter(ShardedCounterServiceImpl.COUNTER_NAME);
		Preconditions.checkNotNull(counterName);

		if (logger.isLoggable(Level.INFO))
		{
			logger.info(String.format("Deleting Counter: %s", counterName));
		}

		this.shardedCounterService.onTaskQueueCounterDeletion(counterName);
	}

	/**
	 * Setter to allow the {@code shardedCounterService} to be set.
	 * 
	 * @param shardedCounterService
	 */
	public void setShardedCounterService(final ShardedCounterService shardedCounterService)
	{
		Preconditions.checkNotNull(shardedCounterService);
		this.shardedCounterService = shardedCounterService;
	}
}
