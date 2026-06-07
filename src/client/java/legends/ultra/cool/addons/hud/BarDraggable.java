package legends.ultra.cool.addons.hud;

public interface BarDraggable {
    boolean isMouseOverBar(double mouseX, double mouseY);

    double getBarX();

    double getBarY();

    void setBarPosition(double x, double y);

    void moveBar(double dx, double dy);

    void clampBar(int screenWidth, int screenHeight);

    void saveBarPosition();
}
