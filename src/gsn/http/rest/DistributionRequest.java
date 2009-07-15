package gsn.http.rest;

import gsn.beans.StreamElement;
import gsn.beans.VSensorConfig;

public interface DistributionRequest {

	public abstract boolean deliverStreamElement(StreamElement se);

	public abstract long getLastVisitedTime();

	public abstract String getQuery();

	public abstract VSensorConfig getVSensorConfig();

	public abstract void close();

	public abstract boolean isClosed();

}