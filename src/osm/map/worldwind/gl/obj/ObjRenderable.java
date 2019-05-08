package osm.map.worldwind.gl.obj;

import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Vec4;

import gov.nasa.worldwind.render.DrawContext;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.Map;
import javax.media.opengl.GL2;
import osm.map.worldwind.gl.GLRenderable;

public class ObjRenderable extends GLRenderable {
	static Map<String, ObjLoader> modelCache = new HashMap<>();
	static Map<String, String> glModelCache = new HashMap<>();
	String modelSource;
	boolean centerit = false, flipTextureVertically = false;
	boolean modelLoading = false;

	private String id;
	private double minumumScale=2;

	public ObjRenderable(Position pos, String modelSource) {
		super(pos);
		this.modelSource = modelSource;
	}

	public ObjRenderable(Position pos, String modelSource, boolean centerit, boolean flipTextureVertically) {
		super(pos);
		this.modelSource = modelSource;
		this.centerit = centerit;
		this.flipTextureVertically = flipTextureVertically;
	}

	public String getModelKey(DrawContext dc) {
		String key = modelSource + "#" + dc.hashCode();
		return key;
	}

	public String getGlModelKey(DrawContext dc) {
		String key = modelSource + "#" + dc.getGL().hashCode();
		return key;
	}

	public void load() {
		ObjLoader ol = new ObjLoader(modelSource,centerit,flipTextureVertically);
		modelCache.put(modelSource, ol);
	}

	protected ObjLoader getModel(final DrawContext dc) {
		String key = this.getModelKey(dc);
		String glKey = this.getGlModelKey(dc);
		ObjLoader model = modelCache.get(modelSource);
		if (modelCache.get(key) == null) {
			if(model == null) {
				modelLoading = true;
				modelCache.put(key, new ObjLoader(modelSource, dc.getGL().getGL2(), centerit, flipTextureVertically));
			} else {
				model.createGraphics(dc.getGL().getGL2(), centerit);
				modelCache.put(key,model);
			}
			glModelCache.put(key,glKey);
		}
		model = modelCache.get(key);
		eyeDistanceOffset = Math.max(Math.max(model.getXWidth(), model.getYHeight()), model.getZDepth());
		String oldGlKey = glModelCache.get(key);
		if(!oldGlKey.equals(glKey)) {
			model.openGlDrawToList(dc.getGL().getGL2());
			glModelCache.put(key, glKey);
		}
		modelLoading = false;
		return modelCache.get(key);
	}

	public static void reload() {
		modelCache.clear();
	}

	@Override
	protected void drawGL(DrawContext dc) {
		if(modelLoading) {
			return;
		}
		GL2 gl = dc.getGL().getGL2();
		gl.glRotated(90, 1, 0, 0);
		ObjLoader l = getModel(dc);
		if (dc.isPickingMode()) {
			l.getBoundingBox().drawUnitCube(dc);
		} else {
			getModel(dc).opengldraw(gl);
			if (this.isHighlighted()) {
				l.getBoundingBox().drawUnitCubeOutline(dc);
			}
		}
	}

	private double getPixelsPerMeter() {
		int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
		return dpi / .0254;
	}

	@Override
	protected double computeSize(DrawContext dc, Vec4 loc) {
		if (this.keepConstantSize) {
			return size;
		}
		if (loc == null) {
			System.err.println("Null location when computing size");
			return 1;
		}
		double d = loc.distanceTo3(dc.getView().getEyePoint());
		double metersPerPixel = dc.getView().computePixelSizeAtDistance(d);
		double dpm = this.getPixelsPerMeter();

		double modelSizeMeters = this.eyeDistanceOffset;
		double modelPixels = modelSizeMeters / metersPerPixel;

		double scale = size / modelPixels;

		if (scale < this.minumumScale) {
			scale = this.minumumScale;
		}
		return scale;
	}

	public void setMinimumScaleSize(double minimumScale) {
		this.minumumScale = minimumScale;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}


}
