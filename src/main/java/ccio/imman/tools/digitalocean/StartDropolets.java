package ccio.imman.tools.digitalocean;

import java.io.IOException;

import com.myjeeva.digitalocean.DigitalOcean;
import com.myjeeva.digitalocean.exception.DigitalOceanException;
import com.myjeeva.digitalocean.exception.RequestUnsuccessfulException;

import ccio.imman.tools.digitalocean.model.ImmanCluster;
import ccio.imman.tools.digitalocean.model.ImmanNode;

public class StartDropolets extends DoAction<Void> {
	
	@Override
	public Void process(DigitalOcean apiClient, ImmanCluster immanCluster) {
		System.out.println("Starting "+immanCluster.getClusterName()+" cluster's droplets");
		for(ImmanNode node : immanCluster.getImageNodes()){
			try {
				System.out.println("Starting droplet: "+node.getDropletName());
				apiClient.powerOnDroplet(node.getDropletId());
			} catch (DigitalOceanException | RequestUnsuccessfulException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public static void main(String... args) throws IOException {
		main(new StartDropolets(), args);
	}

}
