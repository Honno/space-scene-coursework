/* SpaceHermit
 * <desc>
 * dd/mm/yyyy
 * 
 * Scene Graph:
 *  Scene origin
 *  |
 *  +-- 
 */
package SpaceHermit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.newdawn.slick.opengl.Texture;

import GraphicsLab.*;

/**
 * My projects simulates being inside of a space jet. You can push the lever and
 * be warped (FTL travel) to a different, randomly generated place in the
 * universe!
 * <p>
 * Controls:
 * <ul>
 * <li>Press the escape key to exit the application.
 * <li>Hold the x, y and z keys to view the scene along the x, y and z axis,
 * respectively
 * <li>While viewing the scene along the x, y or z axis, use the up and down
 * cursor keys to increase or decrease the viewpoint's distance from the scene
 * origin
 * <li>Press L to lower the sun
 * <li>Press R to raise the sun
 * <li>Press space when the lever is in it's rest position to start the warp
 * protocol
 * </ul>
 */
public class Scene extends GraphicsLab {
	/* declare tools */
	private Random rnd = new Random();

	/* declare aspect ratio */
	private float aspect;
	
	/* declare dT time variables */
	private long lastFrameTime = System.nanoTime();
	private long dT;

	/* declare warp animation variables */
	// stores whether the user is warping
	private boolean warping = false;
	// current mode in animation, 'd' is the default mode
	private char mode = 'd';
	// the tick counter for the current animation mode
	private float tick = 0;
	// 's' mode, before the actual warp begins to provide tension
	private float startStallTickLimit = 8.0f;
	// 'i' mode, fades scene into warp
	private float fadeInTickLimit = 2.0f;
	// 'w' mode, warp active and scene is fully bright
	private float warpingTickLimit = 2.0f;
	// 'o' mode, fades scene out of warp
	private float fadeOutTickLimit = 2.0f;
	// default and current values of ambient lighting
	private float globalAmbient = 0.125f;
	private float currentAmbient;
	// default alpha property of white plane that covers the scene
	private float alpha = 0.0f;
	// range and current point of view angles
	private float povMin = 25.0f;
	private float povMax = 45.0f;
	private float pov = povMax;

	
	
	/*
	 * declare the cockpit that contains check input, update and draw methods
	 * for a cockpit object
	 */
	private Cockpit cockpit;
	
	/* declare cockpit shaking variables */
	// amplitude parameters
	private float ampMaxDefault = 0.5f;
	private float ampWarpMax = 4.0f;
	private float ampMax;
	// period parameters
	private float periodDefault = 10.0f;
	private float period;
	// current translation parameters
	private float shakeX = 0.0f;
	private float shakeY = 0.0f;
	private float shakeZ = 0.0f;
	// current tick
	private float xTick = 0;
	private float yTick = 0;
	private float zTick = 0;

	/* declare background variables */
	// positioning values of background plane
	float bgHeight = 64.0f;
	float bgZ = 96.0f;
	// path values to access skyboxes
	private String pckgDir = "SpaceHermit";
	private String skyboxDir = "skyboxes";
	private String[] skyboxNames = { "corona_ft.png", "redeclipse_ft.png",
			"unnamedspace_ft.jpg", "unnamedspace3_ft.png" };
	// stores loaded skybox textures
	private List<Texture> skyboxes;
	// stores skybox texture currently in use
	private Texture currentSkybox;
	// used to reference previously used skybox
	private int currentSkyboxIndex = -1;

	public static void main(String args[]) {
		new Scene().run(WINDOWED, "Scene", 1.0f);
	}

	protected void initScene() throws Exception {
		// define aspect ratio of program
		aspect = (float) displayMode.getWidth() / displayMode.getHeight();

		// assigns new instance of cockpit class, passes animation scale in
		// constructor so the cockpit's animations runs in-sync with scene
		cockpit = new Cockpit();

		// sets random values for shakebing effect
		resetShake();
		initShake();
		
		// loads skyboxes, and set a random one as current
		skyboxes = loadTextures(pckgDir + "/" + skyboxDir, skyboxNames);
		newSkybox();

		// sets the global ambient lighting to it's default value
		resetFade();

		// supply OpenGL with the properties for the main light
		float ambient0[] = { 0.0625f, 0.0625f, 0.0625f, 1.0f };
		float diffuse0[] = { 0.125f, 0.125f, 0.25f, 1.0f };
		float position0[] = { 0.0f, 16.0f, -16.0f, 1.0f };
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT,
				FloatBuffer.wrap(ambient0));
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE,
				FloatBuffer.wrap(diffuse0));
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR,
				FloatBuffer.wrap(diffuse0));
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION,
				FloatBuffer.wrap(position0));
		// enable the first light
		GL11.glEnable(GL11.GL_LIGHT0);

		// enable lighting calculations
		GL11.glEnable(GL11.GL_LIGHTING);
		// ensure that all normals are re-normalised after transformations
		// automatically
		GL11.glEnable(GL11.GL_NORMALIZE);

	}

	protected void checkSceneInput() {
		cockpit.checkSceneInput();
	}

	protected void updateScene() {
		dT = System.nanoTime() - lastFrameTime;
		lastFrameTime = System.nanoTime();
		tick();
		
		// stores ratio of tick to the tick limit of current animation mode
		float ratio;

		if (!warping) {
			// checks what non-warp modes are active if any
			if(mode == 'o') { // fade out
				ratio = getRatio(fadeOutTickLimit);
				if (ratio > 1) {
					// change mode to default
					mode = 'd';
					tickReset();
					// set global ambient lighting to it's default value
					resetFade();
					// reset pov to default value
					pov = povMax;
				} else {
					// else gradually decrease global ambience and alpha of
					// white screen
					currentAmbient = 1.0f - ratio * (1.0f - globalAmbient);
					alpha = 1.0f - ratio;
					pov = povMin + ratio * (povMax - povMin);
				}
			}

			// updates cockpit, value returned tells scene whether warping has
			// been activated
			warping = cockpit.updateScene(dT, getAnimationScale());

			// if warp has been activated then change mode to start stall
			if (warping) {
				mode = 's';
				tickReset();
			}
		} else {
			// checks what warping modes are active
			switch (mode) {
			case 's': // start stall
				ratio = getRatio(startStallTickLimit);
				if (ratio > 1) {
					// change mode to fade in
					mode = 'i';
					tickReset();
				} else {
					// gradually increase the amplitude and frequency of shakeing effect
					increaseShake((float) tick / (startStallTickLimit + fadeInTickLimit));
				}
				break;
			case 'i': // fade in
				ratio = getRatio(fadeInTickLimit);
				if (ratio > 1) {
					// change mode to warping
					mode = 'w';
					tickReset();
					// set ambience to most extreme value
					currentAmbient = 1.0f;
					// make white screen fully opaque
					alpha = 1.0f;
					// change current skybox texture
					newSkybox();
				} else {
					// increase the amplitude and frequency of shakeing effect
					increaseShake((float) (tick + startStallTickLimit) / (startStallTickLimit + fadeInTickLimit));
					// gradually decrease
					pov = povMax - ratio * (povMax - povMin);
					// increase global ambience and alpha of white screen
					currentAmbient = ratio * (1.0f - globalAmbient)
							+ globalAmbient;
					alpha = ratio;
				}
				break;
			case 'w': // warping
				ratio = getRatio(warpingTickLimit);
				if (ratio >= 1) {
					resetShake();
					// change mode to fade out
					mode = 'o';
					tickReset();
					warping = false;
				}
				break;
			}
			
		}
		nextShake();
	}

	protected void renderScene() {
		// set the global ambient lighting to use current ambient level
		GL11.glLightModel(
				GL11.GL_LIGHT_MODEL_AMBIENT,
				FloatBuffer.wrap(new float[] { currentAmbient, currentAmbient,
						currentAmbient, 1.0f }));

		// draw background
		GL11.glPushMatrix();
		drawBackground(currentSkybox);
		GL11.glPopMatrix();

		// draw cockpit
		GL11.glPushMatrix();
		GL11.glTranslatef(shakeX, shakeY, shakeZ);
		cockpit.renderScene();
		GL11.glPopMatrix();

		// draw the white plane that covers the scene used in fading animations
		GL11.glPushMatrix();
		drawWhitePlane(alpha);
		GL11.glPopMatrix();

	}

	protected void setSceneCamera() {
		// set perspective projection, point of view assigned to a variable
		// modified by warping sequence
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GLU.gluPerspective(pov, aspect, 0.1f, 100.0f);

		// default viewpoint is positioned at the scene origin facing along the
		// negative Z axis
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
	}

	protected void cleanupScene() {// TODO: Clean up your resources here
	}

	/**
	 * Loads provided images into OpenGL textures.
	 * 
	 * @param dir
	 *            the path to the images
	 * @param names
	 *            a array of all the names of the images
	 * @return an arraylist of the textures that loaded successfully
	 */
	private List<Texture> loadTextures(String dir, String[] names) {
		// initiate arraylist that stores successfully loaded textures
		List<Texture> textures = new ArrayList<Texture>();
		// regex pattern that captures the image's type (i.e. png, jpg, etc.)
		Pattern ext = Pattern.compile("\\.(.+)");
		// iterates over every image provided to attempt loading as a texture
		for (String name : names) {
			try {
				// applies regex pattern to image name
				Matcher match = ext.matcher(name);
				match.find();
				// loads texture with the path to image generated by the
				// provided dir value and current name, and provides what is the
				// image's type using captured regex pattern from name
				textures.add(loadTexture(dir + "/" + name, match.group(1)
						.toUpperCase()));
			} catch (Exception e) {
				// captures any errors (i.e. no pattern found, path does not
				// exist, etc.)
				e.printStackTrace();
			}
		}
		return textures;
	}

	/**
	 * Draws a plane to the back of the scene and applies a provided texture to
	 * it.
	 * 
	 * @param texture
	 *            the OpenGL texture to apply to plane
	 */
	private void drawBackground(Texture texture) {
		// disable lighting calculations so that they don't affect
		// the appearance of the texture
		GL11.glPushAttrib(GL11.GL_LIGHTING_BIT);
		GL11.glDisable(GL11.GL_LIGHTING);
		// change the geometry colour to white so that the texture
		// is bright and details can be seen clearly
		Colour.WHITE.submit();
		// enable texturing and bind an appropriate texture
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureID());
		// draw back plane
		Vertex v1 = new Vertex(-bgHeight, -bgHeight, -bgZ); // bottom left
		Vertex v2 = new Vertex(-bgHeight, bgHeight, -bgZ); // top left
		Vertex v3 = new Vertex(bgHeight, bgHeight, -bgZ); // top right
		Vertex v4 = new Vertex(bgHeight, -bgHeight, -bgZ); // bottom right
		// draw the plane geometry
		Util.drawRect(v4, v3, v2, v1);
		// disables textures and reset any local lighting changes
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glPopAttrib();
	}

	/**
	 * Draws a white plane that covers the whole scene with a provided alpha
	 * value applied to it.
	 * 
	 * @param alpha
	 *            the desired transparency of the plane to be drawn
	 */
	private void drawWhitePlane(float alpha) {
		// disable lighting calculations so that they don't affect
		// the appearance of the plane
		GL11.glPushAttrib(GL11.GL_LIGHTING_BIT);
		GL11.glDisable(GL11.GL_LIGHTING);
		// change geometry colour to white with the provided alpha value used
		// for transparency
		GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
		// enable blending and set blend function to interpolate the plane's
		// transparency to the whole scene
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		// draw back plane to (more than) cover the scene
		Vertex v1 = new Vertex(-displayMode.getWidth(),
				-displayMode.getHeight(), -1.0f); // bottom left
		Vertex v2 = new Vertex(-displayMode.getWidth(),
				displayMode.getHeight(), -1.0f); // top left
		Vertex v3 = new Vertex(displayMode.getWidth(), displayMode.getHeight(),
				-1.0f); // top right
		Vertex v4 = new Vertex(displayMode.getWidth(),
				-displayMode.getHeight(), -1.0f); // bottom right
		// draw the plane geometry
		Util.drawRect(v4, v3, v2, v1);
		// disable blending and reset any local lighting changes
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopAttrib();
	}

	/**
	 * Change the current skybox to a new texture, ensuring no skyboxes are
	 * shown twice in a row.
	 */
	private void newSkybox() {
		boolean found = false;
		int index = currentSkyboxIndex;
		// keeps on iterating until a valid skybox index is found with a random
		// number generator
		while (!found) {
			index = rnd.nextInt(skyboxes.size());
			if (index != currentSkyboxIndex) {
				currentSkyboxIndex = index;
				found = true;
			}
		}
		// gets the new skybox texture from the pre-loaded list of skybox
		// textures
		currentSkybox = skyboxes.get(currentSkyboxIndex);
	}

	private void tick() {
		float updateTime = (float) ((dT * Math.pow(10, -9)) * getAnimationScale());
		tick += updateTime;
		xTick += updateTime;
		yTick += updateTime;
		zTick += updateTime;
	}
	
	/**
	 * Resets the tick count.
	 */
	private void tickReset() {
		tick = 0;
	}

	/**
	 * Calculates the ratio of the tick to a given value.
	 * 
	 * @param tickLimit
	 *            the tick limit to
	 * @return
	 */
	private float getRatio(float tickLimit) {
		return (float) tick / tickLimit;
	}

	/**
	 * Sets the current ambient and alpha values to their default state.f
	 */
	private void resetFade() {
		currentAmbient = globalAmbient;
		alpha = 0.0f;
	}
	
	public void resetShake() {
		ampMax = ampMaxDefault;
		period = periodDefault;
	}
	
	public void initShake() {
		xTick = rnd.nextFloat() * period;
		yTick = rnd.nextFloat() * period;
		zTick = rnd.nextFloat() * period;
	}
	
	private void nextShake() {
		double rad = 2 * Math.PI;
		shakeX = (float) Math.sin((xTick / period) * rad) * ampMax;
		shakeY = (float) Math.sin((yTick / period) * rad) * ampMax;
		shakeZ = (float) Math.sin((zTick / period) * rad) * ampMax;
		if(xTick > period) {
			xTick = 0.0f;
		}
		if(yTick > period) {
			yTick = 0.0f;
		}
		if(zTick > period) {
			zTick = 0.0f;
		}
		
	}
	
	private void increaseShake(float ratio) {
		ampMax = ampMaxDefault + ratio * (ampWarpMax - ampMaxDefault);
		period = periodDefault - ratio * periodDefault;
	}
}
