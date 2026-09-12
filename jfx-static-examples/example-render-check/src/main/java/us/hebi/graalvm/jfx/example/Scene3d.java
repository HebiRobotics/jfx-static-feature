package us.hebi.graalvm.jfx.example;

import java.util.List;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

/**
 * The prism 3d path with a textured sphere, a box and a hand-built mesh under a point light.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
class Scene3d implements Scenario {

    // The scene looks down +z from (0,0,-500) with a 30 degree vertical field of view, so a unit at z=0
    // covers 1.12 pixels and the three objects at x -110, 0 and 110 project onto these columns
    private static final int WIDTH = 400, HEIGHT = 300;
    private static final Color BACKGROUND = Color.web("#101018");
    private static final Color BOX_COLOR = Color.web("#c94f7c");
    private static final Color MESH_COLOR = Color.web("#4fc9a4");
    private static final int CAMERA_Z = -500, CENTER_PY = 150;
    private static final int SPHERE_RADIUS = 45, SPHERE_SCENE_X = -110, SPHERE_PX = 77, LIT_OFFSET = 25;
    private static final int BOX_SIZE = 70, BOX_PX = 200;
    private static final int MESH_HALF = 40, MESH_HEIGHT = 55, MESH_SCENE_X = 110, MESH_PX = 323, MESH_SAMPLE = 60;

    @Override
    public String name() {
        return "3d";
    }

    @Override
    public String skipReason() {
        if (Platform.isSupported(ConditionalFeature.SCENE3D)) {
            return null;
        }
        return "ConditionalFeature.SCENE3D is missing on prism.order="
                + System.getProperty("prism.order", "default");
    }

    @Override
    public WritableImage render() {
        PhongMaterial textured = new PhongMaterial(Color.WHITE);
        textured.setDiffuseMap(Pictures.jpeg());
        Sphere sphere = new Sphere(SPHERE_RADIUS);
        sphere.setMaterial(textured);
        sphere.setTranslateX(SPHERE_SCENE_X);

        Box box = new Box(BOX_SIZE, BOX_SIZE, BOX_SIZE);
        box.setMaterial(new PhongMaterial(BOX_COLOR));
        box.getTransforms().addAll(new Rotate(25, Rotate.Y_AXIS), new Rotate(-20, Rotate.X_AXIS));

        MeshView mesh = new MeshView(pyramid());
        mesh.setMaterial(new PhongMaterial(MESH_COLOR));
        // NONE because the winding of the hand-built faces must not decide whether anything shows up
        mesh.setCullFace(CullFace.NONE);
        mesh.setTranslateX(MESH_SCENE_X);
        mesh.setTranslateY(MESH_HEIGHT / 2);
        mesh.getTransforms().addAll(new Rotate(20, Rotate.Y_AXIS), new Rotate(-20, Rotate.X_AXIS));

        // Off to the upper left, so the left side of the sphere comes out brighter than the right
        PointLight point = new PointLight(Color.WHITE);
        point.setTranslateX(-350);
        point.setTranslateY(-250);
        point.setTranslateZ(-450);
        AmbientLight ambient = new AmbientLight(Color.gray(0.15));

        Scene scene = new Scene(new Group(sphere, box, mesh, point, ambient), WIDTH, HEIGHT,
                true, SceneAntialiasing.BALANCED);
        scene.setFill(BACKGROUND);
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(1);
        camera.setFarClip(2000);
        camera.setTranslateZ(CAMERA_Z);
        scene.setCamera(camera);
        return Scenario.snapshot(new Stage(), scene);
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        if (!Pixels.checkSize(problems, image, WIDTH, HEIGHT)) {
            return;
        }
        Pixels.checkPixel(problems, image, 5, HEIGHT - 5, BACKGROUND, "the 3d background");
        Pixels.checkForeground(problems, image, SPHERE_PX, CENTER_PY, 1, 1, BACKGROUND, "the sphere");
        Pixels.checkBrighter(problems, image, SPHERE_PX - LIT_OFFSET, SPHERE_PX + LIT_OFFSET, CENTER_PY,
                "the lit side of the sphere");
        Pixels.checkForeground(problems, image, BOX_PX, CENTER_PY, 1, 1, BACKGROUND, "the box");
        Pixels.checkForeground(problems, image, MESH_PX - MESH_SAMPLE / 2, CENTER_PY - MESH_SAMPLE / 2,
                MESH_SAMPLE, MESH_SAMPLE, BACKGROUND, "the mesh");
    }

    private static TriangleMesh pyramid() {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(
                0, -MESH_HEIGHT, 0,
                -MESH_HALF, 0, -MESH_HALF,
                -MESH_HALF, 0, MESH_HALF,
                MESH_HALF, 0, MESH_HALF,
                MESH_HALF, 0, -MESH_HALF);
        mesh.getTexCoords().addAll(0, 0);
        mesh.getFaces().addAll(
                0, 0, 2, 0, 1, 0,
                0, 0, 3, 0, 2, 0,
                0, 0, 4, 0, 3, 0,
                0, 0, 1, 0, 4, 0,
                1, 0, 2, 0, 3, 0,
                1, 0, 3, 0, 4, 0);
        return mesh;
    }

}
