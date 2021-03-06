package org.java8recipes.chapter16.recipe16_05;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Slider;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaErrorEvent;
import javafx.scene.media.MediaMarkerEvent;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/**
 * Recipe 16-5: Synchronize Animation and Media
 * @author cdea
 * Update J. Juneau
 */
public class SynchAnimationAndMedia extends Application {
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;
    private Point2D anchorPt;
    private Point2D previousLocation;
    private Slider progressSlider;
    private ChangeListener<Duration> progressListener;
    private boolean paused = false;
    private Node pauseMessage;
    private Text closedCaption;
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void start(final Stage primaryStage) {
        primaryStage.setTitle("Chapter 16-5 Synchronize Animation and Media");
        primaryStage.centerOnScreen();
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        
        final Group root = new Group();
        final Scene scene = new Scene(root, 540, 300, Color.rgb(0, 0, 0, 0));
        
        // rounded rectangle with slightly transparent        
        Node applicationArea = createBackground(scene);
        root.getChildren().add(applicationArea);

        // allow the user to drag window on the desktop
        attachMouseEvents(scene, primaryStage);
        
        // allows the user to see the progress of the video playing
        progressSlider = createSlider(scene);
        root.getChildren().add(progressSlider);
                
        // Dragging over surface
        scene.setOnDragOver((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles() || db.hasUrl() || db.hasString()) {
                event.acceptTransferModes(TransferMode.COPY);
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                }
            } else {
                event.consume();
            }
        });
        
        // update slider as video is progressing (later removal)
        progressListener = (ObservableValue<? extends Duration> observable,
                Duration oldValue, Duration newValue) -> {
            progressSlider.setValue(newValue.toSeconds());
        };
        
        // toggle the message pause on the video
        pauseMessage = createPauseMessage("Pause");
        pauseMessage.setOpacity(0);
        root.getChildren().add(pauseMessage);

        final VBox messageArea = createClosedCaptionArea(scene);
        root.getChildren().add(messageArea);

        final TranslateTransition animateTheEnd = createTheEnd(scene);
        root.getChildren().add(animateTheEnd.getNode());
        // Dropping over surface
        scene.setOnDragDropped((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            URI resourceUrlOrFile = null;
            
            // dragged from web browser address line?
            if (db.hasContent(DataFormat.URL)) {
                try {
                    resourceUrlOrFile = new URI(db.getUrl().toString());
                } catch (URISyntaxException ex) {
                    ex.printStackTrace();    
                }
            } else if (db.hasFiles()) {
                // dragged from the file system
                String filePath = null;
                for (File file:db.getFiles()) {
                    filePath = file.getAbsolutePath();
                }
                resourceUrlOrFile = new File(filePath).toURI();
                success = true;
            }
            // load media
            Media media = new Media(resourceUrlOrFile.toString());
            
            // stop previous media player and clean up
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.currentTimeProperty().removeListener(progressListener);
                mediaPlayer.setOnPaused(null);
                mediaPlayer.setOnPlaying(null);
                mediaPlayer.setOnReady(null);
                mediaPlayer.setOnEndOfMedia(null);
                
            }
            
            // create a new media player
            mediaPlayer = new MediaPlayer(media);
            
            // as the media is playing move the slider for progress
            mediaPlayer.currentTimeProperty().addListener(progressListener);
            
            // when paused event display pause message
            mediaPlayer.setOnPaused(() -> {
                pauseMessage.setOpacity(.90);
            });
            
            // when playing make pause text invisible
            mediaPlayer.setOnPlaying(() -> {
                animateTheEnd.stop();
                animateTheEnd.getNode().setTranslateY(scene.getHeight() + 40);
                animateTheEnd.getNode().setOpacity(0);
                pauseMessage.setOpacity(0);
            });
            
            // play video when ready status
            mediaPlayer.setOnReady(() -> {
                progressSlider.setValue(1);
                progressSlider.setMax(mediaPlayer.getMedia().getDuration().toMillis()/1000);
                mediaPlayer.play();
            });
            
            // Lazy init media viewer
            if (mediaView == null) {
                mediaView = new MediaView(mediaPlayer);
                mediaView.setX(4);
                mediaView.setY(4);
                mediaView.setPreserveRatio(true);
                mediaView.setOpacity(.85);
                mediaView.setSmooth(true);
                
                mediaView.fitWidthProperty().bind(scene.widthProperty().subtract(messageArea.widthProperty().add(70)));
                mediaView.fitHeightProperty().bind(scene.heightProperty().subtract(30));
                
                // make media view as the second node on the scene.
                root.getChildren().add(1, mediaView);
            }
            
            // sometimes loading errors occur
            mediaView.setOnError((MediaErrorEvent event1) -> {
                event1.getMediaError().printStackTrace();
            });
            
            mediaView.setMediaPlayer(mediaPlayer);
            
            media.getMarkers().put("First marker", Duration.millis(10000));
            media.getMarkers().put("Second marker", Duration.millis(20000));
            media.getMarkers().put("Last one...", Duration.millis(30000));
            
            mediaPlayer.setOnMarker((MediaMarkerEvent event1) -> {
                closedCaption.setText(event1.getMarker().getKey());
            });
            
            mediaPlayer.setOnEndOfMedia(() -> {
                closedCaption.setText("");
                animateTheEnd.getNode().setOpacity(.90);
                animateTheEnd.playFromStart();
            });
            
            event.setDropCompleted(success);
            event.consume();
        });
        
        // rectangular area holding buttons
        final Group buttonArea = createButtonArea(scene);
        
        // stop button will stop and rewind the media
        Node stopButton = createStopControl();
       
        // play button can resume or start a media 
        final Node playButton = createPlayControl();
        
        // pauses media play
        final Node pauseButton = createPauseControl();

        stopButton.setOnMousePressed((MouseEvent me) -> {
            if (mediaPlayer!= null) {
                buttonArea.getChildren().removeAll(pauseButton, playButton);
                buttonArea.getChildren().add(playButton);
                closedCaption.setText("");
                mediaPlayer.stop();
                pauseMessage.setOpacity(0);
            }
        });
        // pause media and swap button with play button
        pauseButton.setOnMousePressed(new EventHandler<MouseEvent>() {
            public void handle(MouseEvent me) {
                if (mediaPlayer!=null) {
                    buttonArea.getChildren().removeAll(pauseButton, playButton);
                    buttonArea.getChildren().add(playButton);
                    mediaPlayer.pause();
                    paused = true;
                }
            }
        });
        
        // play media and swap button with pause button
        playButton.setOnMousePressed((MouseEvent me) -> {
            if (mediaPlayer != null) {
                buttonArea.getChildren().removeAll(pauseButton, playButton);
                buttonArea.getChildren().add(pauseButton);
                paused = false;
                mediaPlayer.play();
            }
        });
        
        // add stop button to button area
        buttonArea.getChildren().add(stopButton);
        
        // set pause button as default
        buttonArea.getChildren().add(pauseButton);

        // add buttons
        root.getChildren().add(buttonArea);

        // create a close button
        Node closeButton= createCloseButton(scene);
        root.getChildren().add(closeButton);
        
        primaryStage.setOnShown((WindowEvent we) -> {
            previousLocation = new Point2D(primaryStage.getX(), primaryStage.getY());
        });
        
        primaryStage.setScene(scene);
        primaryStage.show();

        
        
    }

    private VBox createClosedCaptionArea(final Scene scene) {
        // create message area
        final VBox messageArea = new VBox(3);
        messageArea.setTranslateY(30);
        messageArea.translateXProperty().bind(scene.widthProperty().subtract(152) );
        messageArea.setTranslateY(20);
        closedCaption = new Text();
        closedCaption.setStroke(Color.WHITE);
        closedCaption.setFill(Color.YELLOW);
        closedCaption.setFont(new Font(15));
        messageArea.getChildren().add(closedCaption);
        return messageArea;
    }

    private Node createPauseMessage(String message) {
        DropShadow dShadow = new DropShadow();
        dShadow.setOffsetX(3.5f);
        dShadow.setOffsetY(3.5f);
         
        Text textMessage = new Text(message);
        textMessage.setX(100);
        textMessage.setY(50);
        textMessage.setStrokeWidth(2);
        textMessage.setStroke(Color.WHITE);
        textMessage.setEffect(dShadow);
        textMessage.setFill(Color.LIME);
        textMessage.setFont(Font.font(null, FontWeight.BOLD, 35));
        textMessage.setTranslateY(50);
                   
        return textMessage;
    }

    private Group createButtonArea(final Scene scene) {
        // create button area
        final Group buttonGroup = new Group();
        // rounded rect
        Rectangle buttonArea = new Rectangle();
        buttonArea.setArcWidth(15);
        buttonArea.setArcHeight(20);
        buttonArea.setFill(new Color(0, 0, 0, .55));
        buttonArea.setX(0);
        buttonArea.setY(0);
        buttonArea.setWidth(60);
        buttonArea.setHeight(30);
        buttonArea.setStroke(Color.rgb(255, 255, 255, .70));
        buttonGroup.getChildren().add(buttonArea);
        
        // move button group when scene is resized
        buttonGroup.translateXProperty().bind(scene.widthProperty().subtract(buttonArea.getWidth() + 6));
        buttonGroup.translateYProperty().bind(scene.heightProperty().subtract(buttonArea.getHeight() + 6));
        return buttonGroup;
    }

    private Node createStopControl() {
        // stop audio control
        Rectangle stopButton = new Rectangle();
        stopButton.setArcWidth(5);
        stopButton.setArcHeight(5);
        stopButton.setFill(Color.rgb(255, 255, 255, .80));
        stopButton.setX(0);
        stopButton.setY(0);
        stopButton.setWidth(10);
        stopButton.setHeight(10);
        stopButton.setTranslateX(15);
        stopButton.setTranslateY(10);
        stopButton.setStroke(Color.rgb(255, 255, 255, .70));

        return stopButton;
    }

    private Node createBackground(Scene scene) {
        // application area
        Rectangle applicationArea = new Rectangle();
        applicationArea.setArcWidth(20);
        applicationArea.setArcHeight(20);
        applicationArea.setFill(Color.rgb(0, 0, 0, .80));
        applicationArea.setX(0);
        applicationArea.setY(0);
        applicationArea.setStrokeWidth(2);
        applicationArea.setStroke(Color.rgb(255, 255, 255, .70));
        applicationArea.widthProperty().bind(scene.widthProperty());
        applicationArea.heightProperty().bind(scene.heightProperty());
        return applicationArea;
    }

    private Slider createSlider(Scene scene) {
        Slider slider = new Slider();
        slider.setMin(0);
        slider.setMax(100);
        slider.setValue(1);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        
        slider.valueProperty().addListener((ObservableValue<? extends Number> observable,
                Number oldValue, Number newValue) -> {
            if (slider.isPressed()) {
                long dur = newValue.intValue() * 1000;
                mediaPlayer.seek(new Duration(dur));
            }
        });
        slider.translateYProperty().bind(scene.heightProperty().subtract(30));
        return slider;
    }

    private void attachMouseEvents(Scene scene, final Stage primaryStage) {
        
        // Full screen toggle
        scene.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2) {
                primaryStage.setFullScreen(!primaryStage.isFullScreen());
            }
        });
        
        // starting initial anchor point
        scene.setOnMousePressed((MouseEvent event) -> {
            if (!primaryStage.isFullScreen()) {
                anchorPt = new Point2D(event.getScreenX(), event.getScreenY());
            }
        });
        
        // dragging the entire stage
        scene.setOnMouseDragged((MouseEvent event) -> {
            if (anchorPt != null && previousLocation != null && !primaryStage.isFullScreen()) {
                primaryStage.setX(previousLocation.getX() + event.getScreenX() - anchorPt.getX());
                primaryStage.setY(previousLocation.getY() + event.getScreenY() - anchorPt.getY());
            }
        });
        
        // set the current location
        scene.setOnMouseReleased((MouseEvent event) -> {
            if (!primaryStage.isFullScreen()) {
                previousLocation = new Point2D(primaryStage.getX(), primaryStage.getY());
            }
        });
    }

    private Node createCloseButton(Scene scene) {
        // close button
        final Group closeApp = new Group();
        Circle closeButton = new Circle();
        closeButton.setCenterX(5);
        closeButton.setCenterY(0);
        closeButton.setRadius(7);
        closeButton.setFill(Color.rgb(255, 255, 255, .80));

        Text closeXmark = new Text(2, 4, "X");
        closeApp.translateXProperty().bind(scene.widthProperty().subtract(15));
        closeApp.setTranslateY(10);
        closeApp.getChildren().addAll(closeButton, closeXmark);
        closeApp.setOnMouseClicked((MouseEvent event) -> {
            Platform.exit();
        });
        return closeApp;
    }

    private Node createPauseControl() {
        // pause control
        final Group pause = new Group();
        final Circle pauseButton = new Circle();
        pauseButton.setCenterX(12);
        pauseButton.setCenterY(16);
        pauseButton.setRadius(10);
        pauseButton.setStroke(new Color(1, 1, 1, .90));
        pauseButton.setTranslateX(30);

        final Line firstLine = new Line();
        firstLine.setStartX(6);
        firstLine.setStartY(16 - 10);
        firstLine.setEndX(6);
        firstLine.setEndY(16 - 2);
        firstLine.setStrokeWidth(3);
        firstLine.setTranslateX(34);
        firstLine.setTranslateY(6);
        firstLine.setStroke(new Color(1, 1, 1, .90));

        final Line secondLine = new Line();
        secondLine.setStartX(6);
        secondLine.setStartY(16 - 10);
        secondLine.setEndX(6);
        secondLine.setEndY(16 - 2);
        secondLine.setStrokeWidth(3);
        secondLine.setTranslateX(38);
        secondLine.setTranslateY(6);
        secondLine.setStroke(new Color(1, 1, 1, .90));

        pause.getChildren().addAll(pauseButton, firstLine, secondLine);
        return pause;
    }

     private Node createPlayControl() {
        // play control
        final Arc playButton = new Arc();
        playButton.setType(ArcType.ROUND);
        playButton.setCenterX(12);
        playButton.setCenterY(16);
        playButton.setRadiusX(15);
        playButton.setRadiusY(15);
        playButton.setStartAngle(180 - 30);
        playButton.setLength(60);
        playButton.setFill(new Color(1, 1, 1, .90));
        playButton.setTranslateX(40);

        return playButton;
    }

    public TranslateTransition createTheEnd(Scene scene) {
        Text theEnd = new Text("The End");
        theEnd.setFont(new Font(40));
        theEnd.setStrokeWidth(3);
        theEnd.setFill(Color.WHITE);
        theEnd.setStroke(Color.WHITE);
        theEnd.setX(75);

        TranslateTransition scrollUp = new TranslateTransition();
        scrollUp.setNode(theEnd);
        scrollUp.setDuration(Duration.seconds(1));
        scrollUp.setInterpolator(Interpolator.EASE_IN);
        scrollUp.setFromY(scene.getHeight() + 40);
        scrollUp.setToY(scene.getHeight()/2);

        return scrollUp;
    }

}