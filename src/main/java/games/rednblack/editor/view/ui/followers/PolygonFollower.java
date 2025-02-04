/*
 * ******************************************************************************
 *  * Copyright 2015 See AUTHORS file.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *****************************************************************************
 */

package games.rednblack.editor.view.ui.followers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import games.rednblack.editor.renderer.components.PolygonComponent;
import games.rednblack.editor.renderer.components.TransformComponent;
import games.rednblack.editor.renderer.utils.PolygonUtils;
import games.rednblack.editor.utils.runtime.SandboxComponentRetriever;
import games.rednblack.editor.view.stage.Sandbox;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by azakhary on 7/2/2015.
 */
public class PolygonFollower extends SubFollower {

    private TransformComponent transformComponent;
    private PolygonComponent polygonComponent;

    private ArrayList<Vector2> originalPoints;
    private Vector2[] drawPoints;

    private ShapeRenderer shapeRenderer;

    public static final int ANCHOR_SIZE = 9;
    public static final int CIRCLE_RADIUS = 10;

    private static final Color outlineColor = new Color(200f / 255f, 156f / 255f, 71f / 255f, 1f);
    private static final Color innerColor = new Color(200f / 255f, 200f / 255f, 200f / 255f, 0.2f);
    private static final Color overColor = new Color(255f / 255f, 94f / 255f, 0f / 255f, 1f);
    private static final Color problemColor = new Color(200f / 255f, 0f / 255f, 0f / 255f, 1f);

    private int lineIndex = -1;
    public int draggingAnchorId = -1;

    private int[] intersections = null;

    private int selectedAnchorId = -1;

    private final int pixelsPerWU;
    private final OrthographicCamera runtimeCamera = Sandbox.getInstance().getCamera();

    public PolygonFollower(int entity) {
        super(entity);
        pixelsPerWU = Sandbox.getInstance().getPixelPerWU();
        setTouchable(Touchable.enabled);
        update();
    }

    public void create() {
        polygonComponent = SandboxComponentRetriever.get(entity, PolygonComponent.class);
        transformComponent = SandboxComponentRetriever.get(entity, TransformComponent.class);
        shapeRenderer = new ShapeRenderer();
    }

    @Override
    protected void setStage(Stage stage) {
        super.setStage(stage);
        if (stage != null) {
            shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
        }
    }

    @Override
    public void update() {
        if(polygonComponent != null && polygonComponent.vertices != null) {
            computeOriginalPoints();
            computeDrawPoints();
            if(selectedAnchorId == -1) selectedAnchorId = 0;
        }
    }

    public void updateDraw() {
        computeDrawPoints();
    }

    private void computeOriginalPoints() {
        originalPoints = new ArrayList<>();
        if(polygonComponent == null) return;

        originalPoints = new ArrayList<>(Arrays.asList(PolygonUtils.mergeTouchingPolygonsToOne(polygonComponent.vertices)));
    }

    private void computeDrawPoints() {
        drawPoints = originalPoints.toArray(new Vector2[0]);
    }


    @Override
    public void draw(Batch batch, float parentAlpha) {
        if(polygonComponent != null && polygonComponent.vertices != null) {
            batch.end();

            Gdx.gl.glLineWidth(1.7f);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
            Matrix4 matrix = batch.getTransformMatrix();
            matrix.translate(-parentFollower.polygonOffsetX, -parentFollower.polygonOffsetY, 0);
            matrix.scale(pixelsPerWU / runtimeCamera.zoom, pixelsPerWU / runtimeCamera.zoom, 1f);
            shapeRenderer.setTransformMatrix(matrix);

            drawTriangulatedPolygons();
            drawOutlines();
            drawPoints();

            Gdx.gl.glDisable(GL20.GL_BLEND);

            batch.begin();
        }
    }

    public void drawOutlines() {
        float scaleX = transformComponent.scaleX * (transformComponent.flipX ? -1 : 1);
        float scaleY = transformComponent.scaleY * (transformComponent.flipY ? -1 : 1);

        if (drawPoints.length > 0) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            for (int i = 1; i < drawPoints.length; i++) {
                shapeRenderer.setColor(outlineColor);
                if (lineIndex == i && draggingAnchorId == -1) {
                    shapeRenderer.setColor(overColor);
                }
                if(checkIfLineIntersects(i - 1)) {
                    shapeRenderer.setColor(problemColor);
                }
                shapeRenderer.line(drawPoints[i].x*scaleX, drawPoints[i].y*scaleY, drawPoints[i - 1].x*scaleX, drawPoints[i - 1].y*scaleY);
            }
            shapeRenderer.setColor(outlineColor);
            if(lineIndex == 0 && draggingAnchorId == -1) {
                shapeRenderer.setColor(overColor);
            }
            if(checkIfLineIntersects(drawPoints.length - 1)) {
                shapeRenderer.setColor(problemColor);
            }
            shapeRenderer.line(drawPoints[drawPoints.length - 1].x*scaleX, drawPoints[drawPoints.length - 1].y*scaleY, drawPoints[0].x*scaleX, drawPoints[0].y*scaleY);
            shapeRenderer.end();
        }

    }

    private boolean checkIfLineIntersects(int index) {
        if(intersections == null) return false;
        for(int i = 0; i < intersections.length; i++) {
            if(intersections[i] == index) return true;
        }

        return false;
    }

    public void drawTriangulatedPolygons() {
        if (polygonComponent.vertices == null) {
            return;
        }
        if(intersections != null) {
            return;
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(innerColor);

        float scaleX = transformComponent.scaleX * (transformComponent.flipX ? -1 : 1);
        float scaleY = transformComponent.scaleY * (transformComponent.flipY ? -1 : 1);

        for (Vector2[] poly : polygonComponent.vertices) {
            for (int i = 1; i < poly.length; i++) {
                shapeRenderer.line(poly[i - 1].x*scaleX, poly[i - 1].y*scaleY, poly[i].x*scaleX, poly[i].y*scaleY);
            }
            if (poly.length > 0)
                shapeRenderer.line(poly[poly.length - 1].x*scaleX, poly[poly.length - 1].y*scaleY, poly[0].x*scaleX, poly[0].y*scaleY);
        }
        shapeRenderer.end();
    }

    public void drawPoints() {
        float scaleX = transformComponent.scaleX * (transformComponent.flipX ? -1 : 1);
        float scaleY = transformComponent.scaleY * (transformComponent.flipY ? -1 : 1);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < originalPoints.size(); i++) {
            shapeRenderer.setColor(Color.WHITE);
            if(selectedAnchorId == i) {
                shapeRenderer.setColor(Color.ORANGE);
            }
            float side = (float) (ANCHOR_SIZE) / ((float)pixelsPerWU / runtimeCamera.zoom);
            float onePixel = 1f/((float)pixelsPerWU / runtimeCamera.zoom);
            shapeRenderer.rect(originalPoints.get(i).x*scaleX-side/2f, originalPoints.get(i).y*scaleY-side/2f, side, side);
            shapeRenderer.setColor(Color.BLACK);
            shapeRenderer.rect(originalPoints.get(i).x*scaleX-side/2f+onePixel, originalPoints.get(i).y*scaleY-side/2f+onePixel, side-2*onePixel, side-2*onePixel);
        }
        shapeRenderer.end();
    }

    public void setListener(final PolygonTransformationListener listener) {
        clearListeners();
        addListener(new ClickListener() {

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                super.touchDown(event, x, y, pointer, button);
                x = (x + parentFollower.polygonOffsetX) / pixelsPerWU;
                y = (y + parentFollower.polygonOffsetY) / pixelsPerWU;
                if(button != Input.Buttons.LEFT) return true;
                int anchorId = anchorHitTest(x, y);

                float scaleX = transformComponent.scaleX * (transformComponent.flipX ? -1 : 1);
                float scaleY = transformComponent.scaleY * (transformComponent.flipY ? -1 : 1);

                if (anchorId >= 0) {
                    draggingAnchorId = anchorId;
                    listener.anchorDown(PolygonFollower.this, anchorId, x*runtimeCamera.zoom/scaleX, y*runtimeCamera.zoom/scaleY);
                } else if (lineIndex > -1) {
                    // not anchor but line is selected gotta make new point
                    listener.vertexDown(PolygonFollower.this, lineIndex, x*runtimeCamera.zoom/scaleX, y*runtimeCamera.zoom/scaleY);
                }
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                float scaleX = transformComponent.scaleX * (transformComponent.flipX ? -1 : 1);
                float scaleY = transformComponent.scaleY * (transformComponent.flipY ? -1 : 1);

                x = (x + parentFollower.polygonOffsetX) / pixelsPerWU;
                y = (y + parentFollower.polygonOffsetY) / pixelsPerWU;
                int anchorId = draggingAnchorId;
                if (anchorId >= 0) {
                    listener.anchorDragged(PolygonFollower.this, anchorId, x*runtimeCamera.zoom/scaleX, y*runtimeCamera.zoom/scaleY);
                } else if (lineIndex > -1) {

                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                float scaleX = transformComponent.scaleX * (transformComponent.flipX ? -1 : 1);
                float scaleY = transformComponent.scaleY * (transformComponent.flipY ? -1 : 1);

                x = (x + parentFollower.polygonOffsetX) / pixelsPerWU;
                y = (y + parentFollower.polygonOffsetY) / pixelsPerWU;

                int anchorId = anchorHitTest(x, y);

                lineIndex = vertexHitTest(x, y);
                if (anchorId >= 0) {
                    listener.anchorUp(PolygonFollower.this, anchorId, button,x*runtimeCamera.zoom/scaleX, y*runtimeCamera.zoom/scaleY);
                } else if (lineIndex > -1) {
                    listener.vertexUp(PolygonFollower.this, lineIndex, x*runtimeCamera.zoom/scaleX, y*runtimeCamera.zoom/scaleY);
                }
                draggingAnchorId = -1;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                x = (x + parentFollower.polygonOffsetX) / pixelsPerWU;
                y = (y + parentFollower.polygonOffsetY) / pixelsPerWU;
                int anchorId = anchorHitTest(x, y);
                lineIndex = vertexHitTest(x, y);
                if(anchorId >= 0) {
                    lineIndex = -1;
                }
                if (lineIndex > -1) {

                }

                return super.mouseMoved(event, x, y);
            }
        });
    }

    @Override
    public Actor hit (float x, float y, boolean touchable) {
        if(originalPoints == null || originalPoints.size() == 0) return null;

        x = (x + parentFollower.polygonOffsetX) / pixelsPerWU;
        y = (y + parentFollower.polygonOffsetY) / pixelsPerWU;

        int anchorId = anchorHitTest(x, y);
        if(anchorId > -1) {
            return this;
        }

        // checking for vertex intersect
        lineIndex = vertexHitTest(x, y);
        if(lineIndex > -1) {
            return this;
        }

        return null;
    }

    private int vertexHitTest(float x, float y) {
        Vector2 tmpVector = new Vector2(x, y);
        int lineIndex = -1;

        float circleSqr = ((float)CIRCLE_RADIUS/pixelsPerWU)*((float)CIRCLE_RADIUS/pixelsPerWU);

        float scaleX = transformComponent.scaleX * (transformComponent.flipX ? -1 : 1);
        float scaleY = transformComponent.scaleY * (transformComponent.flipY ? -1 : 1);

        for (int i = 1; i < drawPoints.length; i++) {
            Vector2 pointOne = drawPoints[i-1].cpy().scl(1f/runtimeCamera.zoom*scaleX, 1f/runtimeCamera.zoom*scaleY);
            Vector2 pointTwo = drawPoints[i].cpy().scl(1f/runtimeCamera.zoom*scaleX, 1f/runtimeCamera.zoom*scaleY);
            if (Intersector.intersectSegmentCircle(pointOne, pointTwo, tmpVector, circleSqr)) {
                lineIndex = i;
                break;
            }
        }
        Vector2 pointOne = drawPoints[drawPoints.length - 1].cpy().scl(1f/runtimeCamera.zoom*scaleX, 1f/runtimeCamera.zoom*scaleY);
        Vector2 pointTwo = drawPoints[0].cpy().scl(1f/runtimeCamera.zoom*scaleX, 1f/runtimeCamera.zoom*scaleY);
        if (drawPoints.length > 0 && Intersector.intersectSegmentCircle(pointOne, pointTwo, tmpVector, circleSqr)) {
            lineIndex = 0;
        }

        if(lineIndex > -1) {
            return lineIndex;
        }

        return -1;
    }

    private int anchorHitTest(float x, float y) {
        if(originalPoints == null || originalPoints.size() == 0) return -1;
        float scaleX = transformComponent.scaleX * (transformComponent.flipX ? -1 : 1);
        float scaleY = transformComponent.scaleY * (transformComponent.flipY ? -1 : 1);

        for (int i = 0; i < drawPoints.length; i++) {
            Circle pointCircle = new Circle(drawPoints[i].x/runtimeCamera.zoom*scaleX, drawPoints[i].y/runtimeCamera.zoom*scaleY, (float)CIRCLE_RADIUS/pixelsPerWU);
            if(pointCircle.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    public int getEntity() {
        return entity;
    }

    public ArrayList<Vector2> getOriginalPoints() {
        return originalPoints;
    }

    public void setSelectedAnchor(int anchorId) {
        if(anchorId == -1) return;

        selectedAnchorId = anchorId;
    }

    public int getSelectedAnchorId() {
        return selectedAnchorId;
    }

    public void getSelectedAnchorId(int id) {
        if(id < 0) id = 0;
        selectedAnchorId = id;
    }

    public void setProblems(int[] intersections) {
        this.intersections = intersections;
    }
}
