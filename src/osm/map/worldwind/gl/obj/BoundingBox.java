package osm.map.worldwind.gl.obj;

import gov.nasa.worldwind.render.DrawContext;
import javax.media.opengl.GL2;

public class BoundingBox {

	boolean centerit = false;
	// Vertices of a unit cube, centered on the origin.
	float[][] v = {
		{-0.5f, 0.5f, -0.5f},
		{-0.5f, 0.5f, 0.5f},
		{0.5f, 0.5f, 0.5f},
		{0.5f, 0.5f, -0.5f},
		{-0.5f, -0.5f, 0.5f},
		{0.5f, -0.5f, 0.5f},
		{0.5f, -0.5f, -0.5f},
		{-0.5f, -0.5f, -0.5f}};

	// Array to group vertices into faces
	int[][] faces = {{0, 1, 2, 3}, {2, 5, 6, 3}, {1, 4, 5, 2}, {0, 7, 4, 1}, {0, 7, 6, 3}, {4, 7, 6, 5}};

	// Normal vectors for each face
	float[][] n = {{0, 1, 0}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {0, -1, 0}};

	public BoundingBox(float dx, float dy, float dz, float bottomPoint, boolean centerit) {
		this.centerit = centerit;
		if (centerit) {
			center(dx, dy, dz, bottomPoint);
		}
	}

	private void center(float dx, float dy, float dz, float bottomPoint) {
		if (!this.centerit) {
			bottomPoint = 0;
		}
		for (float row[] : v) {
			row[0] = row[0] * dx;
			row[1] = row[1] * dy - bottomPoint;
			row[2] = row[2] * dz;
		}
	}

	protected void drawUnitCubeOutline(DrawContext dc) {
		GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.

		gl.glLineWidth(2.5f);
		gl.glColor3f(1.0f, 1.0f, 1.0f);
		for (int[] face : faces) {
			try {
				gl.glBegin(GL2.GL_LINE_LOOP);
				for (int j = 0; j < faces[0].length; j++) {
					gl.glVertex3f(v[face[j]][0], v[face[j]][1], v[face[j]][2]);
				}
			} finally {
				gl.glEnd();
			}
		}
	}

	/**
	 * Draw a unit cube, using the active modelview matrix to orient the shape.
	 *
	 * @param dc Current draw context.
	 * @param dx X scale factor
	 * @param dy Y scale factor
	 * @param dz Z scale factor
	 */
	protected void drawUnitCube(DrawContext dc) {
		// Vertices of a unit cube, centered on the origin.

		// Note: draw the cube in OpenGL immediate mode for simplicity. Real applications should use vertex arrays
		// or vertex buffer objects to achieve better performance.
		GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.
		gl.glBegin(GL2.GL_QUADS);
		try {
			for (int i = 0; i < faces.length; i++) {
				gl.glNormal3f(n[i][0], n[i][1], n[i][2]);

				for (int j = 0; j < faces[0].length; j++) {
					gl.glVertex3f(v[faces[i][j]][0], v[faces[i][j]][1], v[faces[i][j]][2]);
				}
			}
		} finally {
			gl.glEnd();
		}
	}

}