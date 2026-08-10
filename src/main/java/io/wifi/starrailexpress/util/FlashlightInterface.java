package io.wifi.starrailexpress.util;

import org.agmas.noellesroles.client.FlashlightLightProvider;

public interface FlashlightInterface {
    
	FlashlightLightProvider getFlashlight();

	void setFlashlight(FlashlightLightProvider provider);
}
