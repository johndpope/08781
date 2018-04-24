package friendaoke;

import friendaoke.services.CommandService;
import friendaoke.services.VoiceStreamService;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.io.File;

public class Controller {

    @FXML
    public MediaView mediaView;

    @FXML
    public Button playButton;
    public ListView<MediaPlayerItem> videoList;
    public Slider timeSlider;
    public Slider volumeSlider;

    private MediaPlayerItem selectedMediaPlayerItem = null;
    private StringProperty property = null;
    private Service voiceStreamService = null;
    private Service commandService = null;

    @FXML
    public void initialize() {
        videoList.setItems(getMediaItems());
        videoList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            mediaView.setMediaPlayer(observable.getValue().mediaPlayer);
            selectedMediaPlayerItem = observable.getValue();
            selectedMediaPlayerItem.updateValues();
        });
        videoList.getSelectionModel().selectFirst();

        timeSlider.valueProperty().addListener(ov -> {
            if (timeSlider.isValueChanging()) {
                // multiply duration by percentage calculated by slider position
                selectedMediaPlayerItem.mediaPlayer.seek(
                        selectedMediaPlayerItem.duration.multiply(timeSlider.getValue() / 100.0));
            }
        });

        volumeSlider.valueProperty().addListener(ov -> {
            if (volumeSlider.isValueChanging()) {
                selectedMediaPlayerItem.mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
            }
        });

        property = new SimpleStringProperty();
        property.addListener((observable, oldValue, newValue) -> {
            System.out.println("oldValue: " + oldValue);
            System.out.println("newValue: " + newValue);
//            controlMedia();
        });

        voiceStreamService = new VoiceStreamService();
        voiceStreamService.start();

        commandService = new CommandService();
        commandService.start();

        property.bind(commandService.messageProperty());

    }

    public void onPlayButtonClick(ActionEvent actionEvent) {
        controlMedia();
    }

    private void controlMedia() {
        MediaPlayer player = selectedMediaPlayerItem.mediaPlayer;
        MediaPlayer.Status status = player.getStatus();

        if (status == MediaPlayer.Status.UNKNOWN  || status == MediaPlayer.Status.HALTED)
        {
            // don't do anything in these states
            return;
        }

        if ( status == MediaPlayer.Status.PAUSED
                || status == MediaPlayer.Status.READY
                || status == MediaPlayer.Status.STOPPED)
        {
            // rewind the movie if we're sitting at the end
            if (selectedMediaPlayerItem.atEndOfMedia) {
                player.seek(player.getStartTime());
                selectedMediaPlayerItem.atEndOfMedia = false;
            }
            player.play();
            playButton.setText("PAUSE");
        } else {
            player.pause();
            playButton.setText("PLAY");
        }
    }

    public void dispose() {
        for (MediaPlayerItem item : videoList.getItems()) {
            item.mediaPlayer.dispose();
        }
        voiceStreamService.cancel();
        commandService.cancel();
    }

    public ObservableList<MediaPlayerItem> getMediaItems() {
        ObservableList<MediaPlayerItem> mediaItems = FXCollections.observableArrayList(
                i -> new Observable[]{i.mediaName});
        mediaItems.add(new MediaPlayerItem("test1.mp4"));
        mediaItems.add(new MediaPlayerItem("test2.mp4"));
        mediaItems.add(new MediaPlayerItem("test3.mp4"));
        mediaItems.add(new MediaPlayerItem("test4.mp4"));
        return mediaItems;
    }

    private class MediaPlayerItem {
        boolean atEndOfMedia = false;
        Duration duration = null;
        MediaPlayer mediaPlayer = null;
        boolean stopRequested = false;
        StringProperty mediaName = new SimpleStringProperty();
        String videoFolder = "video";
        MediaPlayerItem(String mediaName) {
            this.mediaName.set(mediaName);
            mediaPlayer = new MediaPlayer(new Media(getClass().getResource(videoFolder + File.separator + mediaName).toExternalForm()));
            mediaPlayer.currentTimeProperty().addListener(ov -> updateValues());
            mediaPlayer.setOnReady(() -> duration = mediaPlayer.getMedia().getDuration());
            mediaPlayer.setOnPlaying(() -> {
                if (stopRequested) {
                    mediaPlayer.pause();
                    stopRequested = false;
                }
            });
            mediaPlayer.setOnEndOfMedia(() -> {
                stopRequested = true;
                atEndOfMedia = true;
            });
        }

        void updateValues() {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.READY && timeSlider != null && volumeSlider != null) {
                Platform.runLater(() -> {
                    Duration currentTime = mediaPlayer.getCurrentTime();
                    timeSlider.setDisable(duration.isUnknown());
                    if (!timeSlider.isDisabled()
                            && duration.greaterThan(Duration.ZERO)
                            && !timeSlider.isValueChanging()) {
                        timeSlider.setValue(currentTime.divide(duration).toMillis()
                                * 100.0);
                    }
                    if (!volumeSlider.isValueChanging()) {
                        volumeSlider.setValue((int)Math.round(mediaPlayer.getVolume()
                                * 100));
                    }
                });
            }
        }

        @Override
        public String toString() {
            return mediaName.get();
        }
    }
}
