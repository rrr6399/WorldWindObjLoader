package osm.map.worldwind.gl.obj;

import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;
import foxtrot.Task;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.opengl.GL2;
import javax.media.opengl.GLProfile;
import javax.swing.SwingUtilities;
import osm.map.worldwind.gl.obj.MtlLoader.Material;

/**
 * Note from WorldWindowGLCanvas documentation:
*  Under certain conditions, JOGL replaces the <code>GLContext</code> associated with instances of this class. This then
 * necessitates that all resources such as textures that have been stored on the graphic devices must be regenerated for
 * the new context. World Wind does this automatically by clearing the associated {@link GpuResourceCache}. Objects
 * subsequently rendered automatically re-create those resources. If an application creates its own graphics resources,
 * including textures, vertex buffer objects and display lists, it must store them in the <code>GpuResourceCache</code>
 * associated with the current {@link gov.nasa.worldwind.render.DrawContext} so that they are automatically cleared, and
 * be prepared to re-create them if they do not exist in the <code>DrawContext</code>'s current
 * <code>GpuResourceCache</code> when needed. Examples of doing this can be found by searching for usages of the method
 * {@link GpuResourceCache#get(Object)} and {@link GpuResourceCache#getTexture(Object)}.
 * 
 * TODO: Need to migrate to this approach
 */

public class ObjLoader {

	private static final Map<String, Integer> oldObjectListLookup = new HashMap<>();

	private String modelName;
	List<float[]> vertexSets = new ArrayList<>();
	List<float[]> vertexSetsNorms = new ArrayList<>();
	List<float[]> vertexSetsTexs = new ArrayList<>();
	List<Face> faces = new ArrayList<>();
	int objectlist;
	float topPoint, bottomPoint, leftPoint, rightPoint, farPoint, nearPoint;
	Map<String, Texture> textureCache = new HashMap<>();
	Map<String, TextureData> textureDataCache = new HashMap<>();
	BoundingBox bbox;
	private final static Logger logger = Logger.getLogger(ObjLoader.class.getName());
	private GLProfile glProfile;

	String basePath;
	boolean flipTextureVertically;

	public ObjLoader(String objPath, boolean centered, boolean flipTextureVertically) {
		this.flipTextureVertically = flipTextureVertically;
		String parts[] = parsePath(objPath);
		// only load the data initially
		modelName = parts[1];
		this.loadData(parts[0], parts[1]);
	}

	public ObjLoader(String objPath, GL2 gl, boolean centered, boolean flipTextureVertically) {
		this.flipTextureVertically = flipTextureVertically;
		this.glProfile = gl.getGLProfile();
		String parts[] = parsePath(objPath);
		modelName = parts[1];
		this.loadData(parts[0], parts[1]);
		this.createGraphics(gl, centered);
	}

	public ObjLoader(String basePath, String objPath, GL2 gl, boolean centered, boolean flipTextureVertically) {
		this.flipTextureVertically = flipTextureVertically;
		this.glProfile = gl.getGLProfile();
		modelName = objPath;
		this.loadData(basePath, objPath);
		this.createGraphics(gl, centered);
	}

	private String[] parsePath(String objPath) {
		String path = "";
		objPath = objPath.replaceAll("\\\\", "/");
		int index = objPath.lastIndexOf("/");
		String name;
		if (index < 0) {
			name = objPath;
		} else {
			name = objPath.substring(index + 1);
			path = objPath.substring(0, index);
		}
		return new String[]{path, name};
	}

	private void loadData(String basePath, String objPath) {
		try {
			if (basePath == null) {
				this.basePath = "";
			} else {
				this.basePath = basePath;
			}
			BufferedReader bufferedReader = null;
			InputStream is;
			try {
				is = getInputStream(basePath, objPath);
				bufferedReader = new BufferedReader(new InputStreamReader(is));
				final BufferedReader bufferedReaderLocal = bufferedReader;
				if (SwingUtilities.isEventDispatchThread()) {
					foxtrot.ConcurrentWorker.post(new Task() {
						@Override
						public Object run() throws Exception {
							loadObject(bufferedReaderLocal);
							loadTextureData();
							return null;
						}
					});
				} else {
					loadObject(bufferedReaderLocal);
					loadTextureData();
				}
			} finally {
				if (bufferedReader != null) {
					bufferedReader.close();
				}
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error: could not load " + basePath + "/" + objPath, e);
		}
	}

	/**
	 * Must be done in the thread with the GL context
	 *
	 * @param gl
	 * @param centered
	 */
	final public void createGraphics(GL2 gl, boolean centered) {
		try {
			this.processFacesInEDT(); // this process depends on the GL context, which apparently can't be shared between threads
			if (centered) {
				centerit();
			}
			openGlDrawToList(gl);
			this.bbox = new BoundingBox(this.getXWidth(), this.getYHeight(), this.getZDepth(), this.bottomPoint, centered);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error creating graphics for " + this.basePath, e);
		}
	}

	private InputStream getInputStream(String basePath, String objPath) throws IOException {
		String path = basePath + "/" + objPath;
		InputStream is = this.getClass().getResourceAsStream(path);
		if (is == null) {
			File f = new File(path);
			if (f.exists()) {
				is = new FileInputStream(f);
			}
		}
		return is;
	}

	public BoundingBox getBoundingBox() {
		return bbox;
	}

	private void cleanup() {
		vertexSets.clear();
		vertexSetsNorms.clear();
		vertexSetsTexs.clear();
		faces.clear();
	}

	private void loadObject(BufferedReader br) {
		String mtlID = null;
		MtlLoader mtlLoader = null;
		try {
			boolean firstpass = true;
			String newline;
			while ((newline = br.readLine()) != null) {
				if (newline.length() > 0) {
					newline = newline.trim();

					//Loads vertex coordinates
					if (newline.startsWith("v ")) {
						float coords[] = new float[4];
						newline = newline.substring(2, newline.length());
						StringTokenizer st = new StringTokenizer(newline, " ");
						for (int i = 0; st.hasMoreTokens(); i++) {
							coords[i] = Float.parseFloat(st.nextToken());
						}
						vertexSets.add(coords);
					} else //Loads vertex texture coordinates
					{
						if (newline.startsWith("vt")) {
							float coords[] = new float[4];
							newline = newline.substring(3, newline.length());
							StringTokenizer st = new StringTokenizer(newline, " ");
							for (int i = 0; st.hasMoreTokens(); i++) {
								coords[i] = Float.parseFloat(st.nextToken());
							}
							vertexSetsTexs.add(coords);
						} else //Loads vertex normals coordinates
						{
							if (newline.startsWith("vn")) {
								float coords[] = new float[4];
								newline = newline.substring(3, newline.length());
								StringTokenizer st = new StringTokenizer(newline, " ");
								for (int i = 0; st.hasMoreTokens(); i++) {
									coords[i] = Float.parseFloat(st.nextToken());
								}
								vertexSetsNorms.add(coords);
							} else if (newline.startsWith("f ")) { //Loads face coordinates
								newline = newline.substring(2, newline.length());
								StringTokenizer st = new StringTokenizer(newline, " ");
								int count = st.countTokens();
								int v[] = new int[count];
								int vt[] = new int[count];
								int vn[] = new int[count];
								for (int i = 0; i < count; i++) {
									char chars[] = st.nextToken().toCharArray();
									StringBuilder sb = new StringBuilder();
									char lc = 'x';
									for (int k = 0; k < chars.length; k++) {
										if (chars[k] == '/' && lc == '/') {
											sb.append('0');
										}
										lc = chars[k];
										sb.append(lc);
									}
									StringTokenizer st2 = new StringTokenizer(sb.toString(), "/");
									int num = st2.countTokens();
									v[i] = Integer.parseInt(st2.nextToken());
									if (num > 1) {
										vt[i] = Integer.parseInt(st2.nextToken());
									} else {
										vt[i] = 0;
									}
									if (num > 2) {
										vn[i] = Integer.parseInt(st2.nextToken());
									} else {
										vn[i] = 0;
									}
								}
								faces.add(new Face(mtlLoader.getMtl(mtlID), v, vn, vt));
							} else if (newline.startsWith("mtllib")) { //Loads materials
								mtlLoader = new MtlLoader(basePath, newline.substring(newline.indexOf(" ")).trim());
							} else if (newline.startsWith("usemtl")) { //Uses materials
								mtlID = newline.split("\\s+")[1];
							}
						}
					}
				}
			}
		} catch (IOException e) {
			System.out.println("Failed to read file: " + br.toString());
		} catch (NumberFormatException e) {
			System.out.println("Malformed OBJ file: " + br.toString() + "\r \r" + e.getMessage());
		}
		Collections.sort(faces);
		this.calculateBounds();
	}

	public void processFacesInEDT() {
		for (Face face : this.faces) {
			face.createTexture();
		}
	}

	public void loadTextureData() {
		for (Face face : this.faces) {
			face.createTextureData();
		}
	}

	private void centerit() {
		float xshift = getXWidth() / 2.0F;
		float yshift = getYHeight() / 2.0F;
		float zshift = getZDepth() / 2.0F;
		for (int i = 0; i < vertexSets.size(); i++) {
			float coords[] = vertexSets.get(i);
			coords[0] = coords[0] - leftPoint - xshift;
			coords[1] = coords[1] - bottomPoint; // want to stretch from y=0 to 1
			coords[2] = coords[2] - farPoint - zshift;
			vertexSets.set(i, coords);
		}
		scaleit();
		calculateBounds();
	}

	private void scaleit() {
		float scale = getMaxDimension();
		for (int i = 0; i < vertexSets.size(); i++) {
			float coords[] = vertexSets.get(i);
			coords[0] = coords[0] / scale;
			coords[1] = coords[1] / scale;
			coords[2] = coords[2] / scale;
			vertexSets.set(i, coords);
		}
	}

	private void calculateBounds() {
		boolean firstpass = true;
		for (int i = 0; i < vertexSets.size(); i++) {
			float coords[] = vertexSets.get(i);
			if (firstpass) {
				rightPoint = coords[0];
				leftPoint = coords[0];
				topPoint = coords[1];
				bottomPoint = coords[1];
				nearPoint = coords[2];
				farPoint = coords[2];
				firstpass = false;
			}
			rightPoint = Math.max(coords[0], rightPoint);
			leftPoint = Math.min(coords[0], leftPoint);
			topPoint = Math.max(coords[1], topPoint);
			bottomPoint = Math.min(coords[1], bottomPoint);
			nearPoint = Math.max(coords[2], nearPoint);
			farPoint = Math.min(coords[2], farPoint);
		}
		System.out.println("Origin = " + 
			(this.rightPoint+this.leftPoint)/2.0 + ", " +
			(this.bottomPoint+this.topPoint)/2.0 + ", " +
			(this.nearPoint+this.farPoint)/2.0
		);
	}

	public float getMaxDimension() {
		return Math.max(getZDepth(), Math.max(getXWidth(), getYHeight()));
	}

	public float getXWidth() {
		return rightPoint - leftPoint;
	}

	public float getYHeight() {
		return topPoint - bottomPoint;
	}

	public float getZDepth() {
		return nearPoint - farPoint;
	}

	public int getPolygonCount() {
		return faces.size();
	}

	public void openGlDrawToList(GL2 gl) {
		String lastMapKd = "";
		Texture texture = null;

		if (ObjLoader.oldObjectListLookup.get(modelName) != null) {
			gl.glDeleteLists(ObjLoader.oldObjectListLookup.get(modelName), 1);
		}
		this.objectlist = gl.glGenLists(1);
		ObjLoader.oldObjectListLookup.put(modelName, objectlist);

		gl.glNewList(objectlist, GL2.GL_COMPILE);
		Material mtl = null;
		for (Face face : faces) {
			if (mtl == null || !mtl.name.equals(face.mtl.name)) { //has mtl changed?  if so, set up the new mtl
				mtl = face.mtl;
				if (mtl.map_Kd == null) { //no texture?
					if (texture != null) { //disable previous texture if it's not null
						texture.disable(gl);
						texture = null;
						lastMapKd = "";
					}
				} else if (!lastMapKd.equals(mtl.map_Kd.toString())) { //yes texture, and it changed?
					if (texture != null) {
						texture.disable(gl);
					}
					texture = face.texture;
					texture.enable(gl);
					texture.bind(gl);
					gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
					gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
					gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
					gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
					lastMapKd = mtl.map_Kd.toString();
				}

				//determine color
				gl.glEnable(GL2.GL_COLOR_MATERIAL);
				gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA); //enable alpha (transparency) channel
				gl.glEnable(GL2.GL_BLEND); //and blending
				float[] color = lighten(new float[]{Math.min(1, mtl.Kd[0] + mtl.Ka[0]),
					Math.min(1, mtl.Kd[1] + mtl.Ka[1]), Math.min(1, mtl.Kd[2] + mtl.Ka[2])}, 0.15f);
				gl.glColor4f(color[0], color[1], color[2], mtl.d);
			}

			//draw the polygons for this face
			gl.glBegin(face.polyType);
			for (int w = 0; w < face.v.length; w++) {
				if (face.vn[w] != 0) {
					float[] floats = vertexSetsNorms.get(face.vn[w] - 1);
					gl.glNormal3f(floats[0], floats[1], floats[2]);
				}
				if (face.vt[w] != 0) {
					float[] floats = vertexSetsTexs.get(face.vt[w] - 1);
					if (flipTextureVertically) {
						gl.glTexCoord2f(floats[0], 1f - floats[1]);
					} else {
						gl.glTexCoord2f(floats[0], floats[1]);
					}
				}
				float[] floats = vertexSets.get(face.v[w] - 1);
				gl.glVertex3f(floats[0], floats[1], floats[2]);
			}
			gl.glEnd();
		}
		gl.glDisable(GL2.GL_COLOR_MATERIAL);
		if (texture != null) {
			texture.disable(gl);
		}
		gl.glEndList();
	}

	private float[] lighten(float[] color, float amount) {
		float r = Math.min(1, color[0] + amount);
		float g = Math.min(1, color[1] + amount);
		float b = Math.min(1, color[2] + amount);
		return new float[]{r, g, b};
	}

	public void opengldraw(GL2 gl) {
		gl.glCallList(objectlist);
	}

	private Texture getTexture(String map_Kd) throws IOException {
		if (map_Kd == null) {
			return null;
		}
		if (textureCache.get(map_Kd) == null) {
			InputStream is = null;
			try {
				is = this.getInputStream(basePath, map_Kd);
				String suffix = null;
				String tokens[] = map_Kd.split("\\.");
				if (tokens != null) {
					if (tokens.length > 1) {
						suffix = tokens[tokens.length - 1];
					}
				}
				Texture t = TextureIO.newTexture(is, false, suffix);
				textureCache.put(map_Kd, t);
			} finally {
				if (is != null) {
					is.close();
				}
			}
		}
		return textureCache.get(map_Kd);
	}

	public class Face implements Comparable<Face> {

		MtlLoader.Material mtl;
		int[] v; //face
		int[] vn; //normal
		int[] vt; //texture
		int polyType;
		Texture texture;
		TextureData textureData;

		public Face(MtlLoader.Material mtl, int[] v, int[] vn, int[] vt) {
			this.mtl = mtl;
			this.v = v;
			this.vn = vn;
			this.vt = vt;
			switch (v.length) {
				case 3:
					polyType = GL2.GL_TRIANGLES;
					break;
				case 4:
					polyType = GL2.GL_QUADS;
					break;
				default:
					polyType = GL2.GL_POLYGON;
					break;
			}
		}

		public void createTextureData() {
			if (mtl.map_Kd != null) {
				if (textureDataCache.get(mtl.map_Kd) == null) {
					try {
						textureData = getTextureData(mtl.map_Kd);
						textureDataCache.put(mtl.map_Kd, textureData);
					} catch (Exception e) {
						logger.log(Level.SEVERE, "Exception reading texture: " + mtl.map_Kd, e);
					}
				}
			}
		}

		TextureData getTextureData(String map_Kd) throws IOException {
			InputStream is = null;
			TextureData t;
			try {
				is = getInputStream(basePath, map_Kd);
				String suffix = null;
				String tokens[] = map_Kd.split("\\.");
				if (tokens != null) {
					if (tokens.length > 1) {
						suffix = tokens[tokens.length - 1];
					}
				}
				if (glProfile == null) {
					glProfile = GLProfile.getDefault();
				}
				t = TextureIO.newTextureData(glProfile, is, false, suffix);
			} finally {
				if (is != null) {
					is.close();
				}
			}
			return t;
		}

		private Texture getTextureFromTextureData(String map_Kd) {
			if (map_Kd == null) {
				return null;
			}
			if (textureCache.get(map_Kd) == null) {
				Texture t = TextureIO.newTexture(textureData);
				textureCache.put(map_Kd, t);
			}
			return textureCache.get(map_Kd);
		}

		public void createTexture() {
			if (mtl.map_Kd != null) {
				try {
//					texture = getTexture(mtl.map_Kd);
					texture = getTextureFromTextureData(mtl.map_Kd);
				} catch (Exception e) {
					logger.log(Level.SEVERE, "Exception reading texture: " + mtl.map_Kd, e);
				}
			}
		}

		@Override
		public int compareTo(Face face) {
			if(face == null) {
				return -1;
			}
			if (this.mtl.d > face.mtl.d) { //draw opaque faces first
				return -1;
			} else if (this.mtl.d < face.mtl.d) {
				return 1;
			}

			if (this.texture == null && face.texture != null) { //draw non-textured faces first
				return -1;
			} else if (this.texture != null && face.texture == null) {
				return 1;
			} else if (this.texture != null && face.texture != null) { //order by texture name
				return this.mtl.map_Kd.compareTo(face.mtl.map_Kd);
			}
			return this.mtl.name.compareTo(face.mtl.name); //order by mtl name
		}

	}

}
