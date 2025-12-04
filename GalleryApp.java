package cs1302.gallery;

import java.net.http.HttpClient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import java.util.ArrayList;
import java.util.List;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Represents an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    private TextField termField;
    private ComboBox<String> mediaBox;
    private Button getImagesButton;
    private Button playPauseButton;
    private Label messageLabel;
    private ProgressBar progressBar;
    private List<ImageView> slots = new ArrayList<ImageView>(20);

    private boolean playing = false;
    private List<Image> extraImages = new ArrayList<Image>();

    private Timer slideshowTimer;
    private Random random = new Random();

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                          // enable nice output when printing
        .create();                                    // builds and returns a Gson object

    private Stage stage;
    private Scene scene;
    private VBox root;

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new VBox();
    } // GalleryApp

    /** {@inheritDoc} */
    @Override
    public void init() {
        // feel free to modify this method
        System.out.println("init() called");
    } // init

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        this.stage = stage;

        setupUI();

        this.scene = new Scene(this.root, 1000, 650);

        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp!");
        this.stage.setScene(this.scene);
        this.stage.sizeToScene();
        this.stage.show();
        Platform.runLater(() -> this.stage.setResizable(false));
    } // start

    /** {@inheritDoc} */
    @Override
    public void stop() {
        // feel free to modify this method
        System.out.println("stop() called");
    } // stop

    /**
     * Builds and arranges the main user interface for the GalleryApp.
     * This method creates the search bar, message bar, image grid,
     * and status bar, and then adds them to the root layout.
     */
    private void setupUI() {

        HBox searchBar = createSearchBar();
        HBox messageBar = createMessageBar();
        GridPane grid = createImageGrid();
        HBox statusBar = createStatusBar();

        wireHandlers();

        root.getChildren().clear();
        root.getChildren().addAll(searchBar, messageBar, grid, statusBar);
    } // setupUI

    /**
     * Creates the top search bar containing the query field, media selector,
     * and the Get Images and Play/Pause buttons.
     *
     * @return an HBox containing the search controls
     */
    private HBox createSearchBar() {

        termField = new TextField("jack johnson");
        termField.setPrefColumnCount(20);

        mediaBox = new ComboBox<String>();
        mediaBox.getItems().addAll("music", "movie", "podcast", "musicVideo");
        mediaBox.getSelectionModel().select("music");

        getImagesButton = new Button("Get Images");
        playPauseButton = new Button("Play");

        HBox searchBar = new HBox(
            10,
            new Label("Query:"), termField,
            new Label("Media:"), mediaBox,
            getImagesButton, playPauseButton
        );
        searchBar.setPadding(new Insets(8));

        return searchBar;
    } // createSearchBar

    /**
     * Creates the message bar that displays status messages to the user.
     *
     * @return an HBox containing the message label
     */
    private HBox createMessageBar() {

        messageLabel = new Label("Ready.");
        HBox messageBar = new HBox(messageLabel);
        messageBar.setPadding(new Insets(0, 8, 8, 8));

        return messageBar;
    } // createMessageBar

    /**
     * Creates the main grid containing twenty image slots. Each slot
     * initially displays the default placeholder image.
     *
     * @return a GridPane populated with image objects
     */
    private GridPane createImageGrid() {

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setPadding(new Insets(8));

        Image defaultImg = new Image("file:resources/default.png", true);

        int cols = 5;
        int rows = 4;
        int total = cols * rows;

        slots.clear();

        for (int i = 0; i < total; i++) {
            ImageView iv = new ImageView(defaultImg);
            iv.setFitWidth(100);
            iv.setFitHeight(100);
            iv.setPreserveRatio(true);

            slots.add(iv);
            grid.add(iv, i % cols, i / cols);
        }

        return grid;
    } // createImageGrid

    /**
     * Creates the bottom status bar containing the progress bar.
     *
     * @return an HBox containing the progress bar and label
     */
    private HBox createStatusBar() {

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        HBox statusBar = new HBox(10, new Label("Progress:"), progressBar);
        statusBar.setPadding(new Insets(8));

        return statusBar;
    } // createStatusBar

    /**
     * Attaches event handlers to user controls on the interface.
     */
    private void wireHandlers() {

        getImagesButton.setOnAction(e -> onGetImages());
        playPauseButton.setOnAction(e -> togglePlay());
        termField.setOnAction(e -> onGetImages());

    } // wireHandlers

    /**
     * Handles the Get Images button action. This method validates user
     * input, prepares the UI for a search, and starts a background worker thread.
     */
    private void onGetImages() {

        stopSlideshowIfPlaying();

        String term = termField.getText().trim();
        String media = mediaBox.getValue();

        if (!isValidTerm(term)) {
            return;
        }

        prepareForSearch();
        startSearchWorker(term, media);

    } // onGetImages

    /**
     * Stops the slideshow if it is currently running. This ensures that
     * new image searches always reset the slideshow state.
     */
    private void stopSlideshowIfPlaying() {
        if (playing) {
            stopSlideshow();
        }
    } // stopSlideshowIfPlaying

    /**
     * Checks whether the user's search term is valid.
     *
     * @param term the trimmed contents of the search field
     * @return true if the term is non-empty and false otherwise
     */
    private boolean isValidTerm(String term) {
        if (term.length() == 0) {
            messageLabel.setText("Please enter a search term.");
            return false;
        }
        return true;
    } // isValidTerm

    /**
     * Updates the user interface to reflect that a search is starting.
     */
    private void prepareForSearch() {
        messageLabel.setText("Searching iTunes...");
        progressBar.setProgress(0.0);
        getImagesButton.setDisable(true);
    } // prepareForSearch

    /**
     * Starts a background worker thread that contacts the iTunes API
     * and retrieves artwork URLs.
     *
     * @param term the search term
     * @param media the media type selected by the user
     */
    private void startSearchWorker(final String term, final String media) {

        Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<String> urls = fetchArtworkUrls(term, media);
                        handleSearchResults(urls);
                    } catch (Exception ex) {
                        handleSearchError(ex);
                    }
                }
            });

        worker.setDaemon(true);
        worker.start();
    } // startSearchWorker

    /**
     * Processes the results returned from the iTunes API. If fewer than
     * twenty-one distinct image URLs are found, the user is notified.
     * Otherwise, images are loaded in a background thread.
     *
     * @param urls a list of distinct artwork URLs
     */
    private void handleSearchResults(final List<String> urls) {

        if (urls.size() < 21) {
            handleNotEnoughImages();
            return;
        }

        loadImages(urls);

        Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    getImagesButton.setDisable(false);
                    messageLabel.setText("Images loaded.");
                }
            });
    } // handleSearchResults

    /**
     * Displays an alert and resets the UI when the search returns fewer
     * than twenty-one distinct artwork URLs.
     */
    private void handleNotEnoughImages() {

        Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    getImagesButton.setDisable(false);
                    progressBar.setProgress(0.0);
                    messageLabel.setText("Found fewer than 21 images. Try another search.");

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setHeaderText("Not enough images");
                    alert.setContentText(
                        "This search returned fewer than 21 distinct images.\n"
                        + "Please try a different term or media type."
                    );
                    alert.showAndWait();
                }
            });
    } // handleNotEnoughImages

    /**
     * Handles exceptions thrown during the search process. This method
     * resets the UI and displays an error alert to the user.
     *
     * @param ex the exception that occurred during the search
     */
    private void handleSearchError(final Exception ex) {

        Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    getImagesButton.setDisable(false);
                    progressBar.setProgress(0.0);
                    messageLabel.setText("Error: could not load images.");

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setHeaderText("Problem contacting iTunes");
                    alert.setContentText(
                        "There was a problem getting data from the iTunes API.\n"
                        + "Please try again.\n\nDetails: " + ex.getMessage()
                    );
                    alert.showAndWait();
                }
            });

    } // handleSearchError

    /**
     * Contacts the iTunes Search API using the provided search term and media
     * type, parses the JSON response, and returns a list of artwork URLs.
     *
     * @param term  the search term provided by the user
     * @param media the selected media type
     * @return a list of distinct artworkUrl100 strings
     * @throws Exception if an HTTP or JSON processing error occurs
     */
    private List<String> fetchArtworkUrls(String term, String media) throws Exception {

        String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);
        String url = buildItunesUrl(encodedTerm, media);
        URI uri = URI.create(url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .GET()
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        checkStatusCode(response);

        String body = response.body();
        ItunesResponse itunesResponse = GSON.fromJson(body, ItunesResponse.class);

        return collectArtworkUrls(itunesResponse);
    } // fetchArtworkUrls

    /**
     * Builds a properly formatted iTunes Search API request URL using the
     * encoded search term and media type.
     *
     * @param encodedTerm the URL-encoded search term
     * @param media the selected media type
     * @return the complete request URL as a string
     */
    private String buildItunesUrl(String encodedTerm, String media) {

        String url = "https://itunes.apple.com/search"
            + "?term=" + encodedTerm
            + "&media=" + media
            + "&limit=200"
            + "&country=US";

        return url;
    } // buildItunesUrl

    /**
     * Checks the HTTP response status code and throws a runtime exception
     * if the request was not successful.
     *
     * @param response the HTTP response received from the iTunes API
     */
    private void checkStatusCode(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP error: " + response.statusCode());
        }
    } // checkStatusCode

    /**
     * Gets the artworkUrl100 strings from the new iTunes response object.
     *
     * @param itunesResponse the new JSON object
     * @return a list of artwork URLs
     */
    private List<String> collectArtworkUrls(ItunesResponse itunesResponse) {

        List<String> urls = new ArrayList<String>();

        if (itunesResponse != null && itunesResponse.results != null) {
            for (int i = 0; i < itunesResponse.results.length; i++) {
                ItunesResult r = itunesResponse.results[i];
                if (r != null && r.artworkUrl100 != null) {
                    String artwork = r.artworkUrl100;
                    if (!urls.contains(artwork)) {
                        urls.add(artwork);
                    }
                }
            }
        }

        return urls;
    } // collectArtworkUrls

    /**
     * Loads images from a list of artwork URLs. Image creation is performed
     * on the background worker thread.
     *
     * @param urls the list of artwork URLs to load
     */
    private void loadImages(List<String> urls) {

        List<Image> allImages = createImagesFromUrls(urls);
        updateUiWithLoadedImages(allImages);

    } // loadImages

    /**
     * Creates JavaFX Image objects from the provided URL list. Progress is
     * reported to the progress bar.
     *
     * @param urls a list of artwork URLs
     * @return a list of JavaFX Image objects
     */
    private List<Image> createImagesFromUrls(List<String> urls) {

        List<Image> allImages = new ArrayList<Image>();
        int total = urls.size();

        for (int i = 0; i < total; i++) {

            String url = urls.get(i);
            Image img = new Image(url, true);
            allImages.add(img);

            final double progress = (i + 1) / (double) total;

            Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress(progress);
                    }
                });
        }

        return allImages;
    } // createImagesFromUrls

    /**
     * Updates the twenty image slots with the new loaded images and stores
     * any remaining images for slideshow use.
     *
     * @param allImages the list of all loaded Image objects
     */
    private void updateUiWithLoadedImages(final List<Image> allImages) {

        Platform.runLater(new Runnable() {
                @Override
                public void run() {

                    int count = slots.size();
                    if (allImages.size() < count) {
                        count = allImages.size();
                    }

                    for (int i = 0; i < count; i++) {
                        ImageView iv = slots.get(i);
                        Image img = allImages.get(i);
                        iv.setImage(img);
                    }

                    extraImages.clear();
                    for (int i = 20; i < allImages.size(); i++) {
                        extraImages.add(allImages.get(i));
                    }

                    progressBar.setProgress(1.0);
                }
            });

    } // updateUiWithLoadedImages

    /**
     * Called when the user clicks the Play / Pause button.
     * Toggles the slideshow on and off.
     */
    private void togglePlay() {

        if (!playing) {
            if (extraImages.isEmpty()) {
                messageLabel.setText("Load images first before starting slideshow.");
                return;
            }

            startSlideshow();

        } else {
            stopSlideshow();
        }
    } // togglePlay

    /**
     * Start the slideshow that randomly replaces images in the grid.
     */
    private void startSlideshow() {

        if (slideshowTimer != null) {
            slideshowTimer.cancel();
        }

        playing = true;
        playPauseButton.setText("Pause");
        messageLabel.setText("Slideshow playing...");

        slideshowTimer = new Timer(true);

        slideshowTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                if (slots.isEmpty() || extraImages.isEmpty()) {
                                    return;
                                }

                                int slotIndex = random.nextInt(slots.size());

                                int poolIndex = random.nextInt(extraImages.size());

                                ImageView iv = slots.get(slotIndex);
                                Image img = extraImages.get(poolIndex);

                                iv.setImage(img);
                            }
                        });
                }
            }, 1000, 1000);
    } // startSlideshow


    /**
     * Stops the current slideshow.
     */
    private void stopSlideshow() {

        if (slideshowTimer != null) {
            slideshowTimer.cancel();
            slideshowTimer = null;
        }

        playing = false;
        playPauseButton.setText("Play");
        messageLabel.setText("Slideshow paused.");
    } // stopSlideshow


} // GalleryApp
