package org.forgather;

import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
/**
 * Class which acts to control the imageView window, communicates with main controller class
 */
public class iViewController {

    // Associated FXML tags
    @FXML private ImageView cardImage;

    // Class Variables
    private Image back = new Image("/images/back.jpg");

    /**
     * Sets cardImage to default image (back)
     */
    public void reset() {

        cardImage.setImage(back);

    }

    /**
     * Updates cardImage to be the contents of image found at URL
     *
     * @param URL A string indicating where to find the image
     */
    public void set(String URL) {

        // Fetch image
        Image i = new Image(URL);
        cardImage.setImage(i);

    }






}
