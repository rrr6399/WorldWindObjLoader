package osm.map.worldwind.gl.obj;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.terrain.HighResolutionTerrain;
import gov.nasa.worldwind.view.orbit.BasicOrbitView;
import gov.nasa.worldwindx.examples.ApplicationTemplate;
import static gov.nasa.worldwindx.examples.ApplicationTemplate.insertBeforeCompass;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Timer;

public class Tester extends ApplicationTemplate {

	public static class AppFrame extends ApplicationTemplate.AppFrame {

		Position pos;
		RenderableLayer layer;
		Timer timer;
//		double alt = 180;
		double alt = 179;
		private ObjRenderable renderable;

		public AppFrame() {

			String copter = "/models/f550/f550.obj";
//          String ep3="C:/RaptorX/projects/models/ep3/ep3b.obj";
//			String copter = "/models/s900/copter.obj";
//			String copter = "/models/tmrs2/tmrs2.obj";
//			String copter = "/models/matrice/matrice.obj";

			layer = new RenderableLayer();
//			pos = Position.fromDegrees(30, -100, alt);
			HighResolutionTerrain hrt = new HighResolutionTerrain(this.getWwd().getModel().getGlobe(), 30.0);
			pos = Position.fromDegrees(35.77750, -120.80565, alt);
			pos = new Position(pos, hrt.getElevation(pos));

//			this.renderable = new ObjRenderable(pos, f550, true, false);
			this.renderable = new ObjRenderable(pos, copter, true, false);
			long t0 = System.currentTimeMillis();
			this.renderable.load();
			long t1 = System.currentTimeMillis();
			System.out.println("Time to load model (seconds): " + (t1-t0)/1000.0);
			this.renderable.setSize(400);
			this.renderable.setKeepConstantSize(false);
//			this.renderable.setAzimuth(90);
//			this.renderable.setRoll(90);
//			this.renderable.setElevation(-90);
//			this.renderable.setRenderDistance(50000);
//          model.setYaw(55);
			layer.addRenderable(this.renderable);
			layer.setPickEnabled(true);

			BasicOrbitView bv = (BasicOrbitView) this.getWwd().getView();

			class MyBasicOrbitView extends BasicOrbitView {

				public double computeNearClipDistance() {
					return .1;
				}
			};

			MyBasicOrbitView mybv = new MyBasicOrbitView();

			this.getWwd().setView(mybv);
			insertBeforeCompass(getWwd(), layer);

			this.timer = new Timer(1000, new ActionListener() {
				boolean first = true;

				@Override
				public void actionPerformed(ActionEvent e) {
					if (first) {
						gotoPos();
						first = false;
					} else {
						updatePosition();
					}
				}
			});
			timer.start();

			this.wwjPanel.getWwd().addSelectListener(new SelectListener() {
				@Override
				public void selected(SelectEvent event) {
//					System.out.println(event.getTopObject());
				}

			});
		}

		private void gotoPos() {
			this.getWwd().getView().goTo(pos, alt);
		}

		private void updatePosition() {
//			pos = pos.add(Position.fromDegrees(.0001, .0001));
//			model.setPosition(pos);
			this.renderable.setPosition(pos);
//			double yaw=this.renderable.getAzimuth();
//			this.renderable.setAzimuth(yaw+=15);
//			model.setYaw(model.getYaw()+45); // roll
//			model.setRoll(model.getRoll()+45);
//			model.setPitch(model.getPitch()+45);
//			model.setPitch(model.getPitch()+(Math.random()-.5)*5);
			layer.firePropertyChange(AVKey.LAYER, null, this);
			//this.getWwd().redraw();
//			timer.stop();
		}

		protected RenderableLayer getLayer() {
			for (Layer layer : getWwd().getModel().getLayers()) {
				if (layer.getName().contains("Renderable")) {
					return (RenderableLayer) layer;
				}
			}

			return null;
		}

	}

	public static void main(String[] args) {
		ApplicationTemplate.start("World Wind Cones", AppFrame.class);
	}
}
