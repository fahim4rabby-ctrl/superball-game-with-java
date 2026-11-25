import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;


public class SuperBall extends JPanel implements ActionListener {

    // ---------- constants ----------
    static final int W = 800, H = 600;
    static final int R = 9;                       // ball radius
    static final int PADDLE_Y = H - 50, PADDLE_H = 14;
    static final int COLS = 10, BW = 70, BH = 22, GAP = 4;
    static final double NORMAL_PADDLE = 110, WIDE_PADDLE = 180;

    // credit shown in the corner of the start screen (edit the source line to match where YOUR file came from)
    static final String MUSIC_CREDIT = "Music: \"U Can't Touch This\" - MC Hammer";
    static final String MUSIC_SOURCE = "Source: BUGG Lyrics (YouTube) - used for personal/learning purposes";

    enum State { MENU, PLAYING, PAUSED, GAME_OVER }

    // ---------- small data classes ----------
    static class Brick {
        int x, y, hp, maxHp;
        Color color;
        Brick(int x, int y, int hp, Color c) { this.x = x; this.y = y; this.hp = hp; this.maxHp = hp; this.color = c; }
    }

    static class PowerUp {
        double x, y;
        int type; // 0 = super, 1 = wide, 2 = life
        PowerUp(double x, double y, int type) { this.x = x; this.y = y; this.type = type; }
    }

    static class Particle {
        double x, y, vx, vy;
        int life, maxLife;
        Color c;
        Particle(double x, double y, double vx, double vy, int life, Color c) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.maxLife = life; this.c = c;
        }
    }

    // ---------- game state ----------
    final Random rnd = new Random();
    final javax.swing.Timer timer = new javax.swing.Timer(16, this);

    State state = State.MENU;
    final List<Brick> bricks = new ArrayList<>();
    final List<PowerUp> powerUps = new ArrayList<>();
    final List<Particle> particles = new ArrayList<>();
    final LinkedList<double[]> trail = new LinkedList<>();

    double paddleX, paddleW = NORMAL_PADDLE;
    double bx, by, vx, vy;
    boolean launched;
    boolean left, right;
    int score, best, lives, level;
    int superTimer, wideTimer;

    // music (put music.wav in the project folder)
    Clip music;
    boolean musicOn = true;
    String musicStatus = "";

    // background: New York City, Park Avenue at dusk (drawn in code, no image files needed)
    BufferedImage cityBg;

    public SuperBall() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT: case KeyEvent.VK_A: left = true; break;
                    case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: right = true; break;
                    case KeyEvent.VK_SPACE: action(); break;
                    case KeyEvent.VK_P:
                        if (state == State.PLAYING) { state = State.PAUSED; pauseMusic(); }
                        else if (state == State.PAUSED) { state = State.PLAYING; playMusic(); }
                        break;
                    case KeyEvent.VK_M:
                        musicOn = !musicOn;
                        if (musicOn && state == State.PLAYING) playMusic(); else pauseMusic();
                        break;
                    case KeyEvent.VK_R: startGame(); break;
                }
            }
            @Override public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT: case KeyEvent.VK_A: left = false; break;
                    case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: right = false; break;
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { movePaddleTo(e.getX()); }
            @Override public void mouseDragged(MouseEvent e) { movePaddleTo(e.getX()); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { action(); }
        });

        resetPaddle();
        buildLevel();
        loadMusic();
        buildCity();
        timer.start();
    }

    // ---------- game flow ----------
    void action() {
        if (state == State.MENU || state == State.GAME_OVER) startGame();
        else if (state == State.PAUSED) { state = State.PLAYING; playMusic(); }
        else if (state == State.PLAYING && !launched) launch();
    }

    void startGame() {
        score = 0; lives = 3; level = 1;
        resetPaddle();
        buildLevel();
        state = State.PLAYING;
        if (music != null) music.setFramePosition(0);
        playMusic();
    }

    // ---------- music ----------
    void loadMusic() {
        String[] paths = {"music.wav", "src/music.wav", "resources/music.wav"};
        for (String p : paths) {
            File f = new File(p);
            if (!f.exists()) continue;
            try (AudioInputStream in = AudioSystem.getAudioInputStream(f)) {
                music = AudioSystem.getClip();
                music.open(in);
                musicStatus = "Music loaded OK (" + f.getPath() + ")";
                return;
            } catch (Exception ex) {
                musicStatus = "music.wav found but cannot be played - export as 16-bit PCM WAV";
                System.out.println("Could not load music: " + ex);
                return;
            }
        }
        musicStatus = "music.wav NOT FOUND - put it in: " + new File(".").getAbsolutePath();
        System.out.println(musicStatus);
    }

    void playMusic() {
        if (music != null && musicOn && !music.isRunning()) music.loop(Clip.LOOP_CONTINUOUSLY);
    }

    void pauseMusic() {
        if (music != null) music.stop();
    }

    void resetPaddle() {
        paddleW = NORMAL_PADDLE;
        paddleX = (W - paddleW) / 2;
    }

    void movePaddleTo(int mouseX) {
        paddleX = clamp(mouseX - paddleW / 2, 0, W - paddleW);
    }

    double speed() { return Math.min(5.5 + level * 0.5, 10); }

    void launch() {
        launched = true;
        double angle = Math.toRadians(rnd.nextInt(51) - 25);
        double s = speed();
        vx = s * Math.sin(angle);
        vy = -s * Math.cos(angle);
    }

    void buildLevel() {
        bricks.clear();
        powerUps.clear();
        particles.clear();
        trail.clear();
        int rows = Math.min(4 + level, 9);
        int startX = (W - (COLS * BW + (COLS - 1) * GAP)) / 2;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                if (level >= 3 && rnd.nextInt(8) == 0) continue; // gaps for variety
                int hp = (r < Math.min(level - 1, 3)) ? 2 : 1;
                Color col = Color.getHSBColor(r / 10f + 0.02f, 0.65f, 0.95f);
                bricks.add(new Brick(startX + c * (BW + GAP), 60 + r * (BH + GAP), hp, col));
            }
        }
        launched = false;
        superTimer = 0;
        if (wideTimer > 0) wideTimer = 0;
        paddleW = NORMAL_PADDLE;
        paddleX = clamp(paddleX, 0, W - paddleW);
        bx = paddleX + paddleW / 2;
        by = PADDLE_Y - R;
    }

    // ---------- main loop ----------
    @Override public void actionPerformed(ActionEvent e) {
        update();
        repaint();
    }

    void update() {
        // particles keep animating even when not playing
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.x += p.vx; p.y += p.vy; p.vy += 0.12; p.life--;
            if (p.life <= 0) it.remove();
        }
        if (state != State.PLAYING) return;

        // paddle keyboard movement
        if (left) paddleX -= 9;
        if (right) paddleX += 9;
        paddleX = clamp(paddleX, 0, W - paddleW);

        // timers
        if (superTimer > 0) superTimer--;
        if (wideTimer > 0 && --wideTimer == 0) {
            double center = paddleX + paddleW / 2;
            paddleW = NORMAL_PADDLE;
            paddleX = clamp(center - paddleW / 2, 0, W - paddleW);
        }

        updatePowerUps();

        if (!launched) {
            bx = paddleX + paddleW / 2;
            by = PADDLE_Y - R;
            return;
        }

        // move ball
        bx += vx;
        by += vy;
        trail.addFirst(new double[]{bx, by});
        if (trail.size() > 12) trail.removeLast();

        // walls
        if (bx - R < 0) { bx = R; vx = Math.abs(vx); }
        if (bx + R > W) { bx = W - R; vx = -Math.abs(vx); }
        if (by - R < 0) { by = R; vy = Math.abs(vy); }

        // paddle
        if (vy > 0 && by + R >= PADDLE_Y && by + R <= PADDLE_Y + PADDLE_H + 10
                && bx >= paddleX - R && bx <= paddleX + paddleW + R) {
            double hit = (bx - (paddleX + paddleW / 2)) / (paddleW / 2);   // -1 .. 1
            hit = clamp(hit, -1, 1);
            double angle = hit * Math.toRadians(62);
            double s = speed();
            vx = s * Math.sin(angle);
            vy = -s * Math.cos(angle);
            by = PADDLE_Y - R;
        }

        // bricks
        boolean bounced = false;
        for (Iterator<Brick> it = bricks.iterator(); it.hasNext(); ) {
            Brick b = it.next();
            double cx = clamp(bx, b.x, b.x + BW);
            double cy = clamp(by, b.y, b.y + BH);
            double dx = bx - cx, dy = by - cy;
            if (dx * dx + dy * dy > R * R) continue;

            if (superTimer > 0) {              // super ball smashes everything
                destroy(b);
                it.remove();
                continue;
            }
            if (bounced) continue;
            bounced = true;

            if (Math.abs(dx) > Math.abs(dy)) vx = dx > 0 ? Math.abs(vx) : -Math.abs(vx);
            else if (dy != 0) vy = dy > 0 ? Math.abs(vy) : -Math.abs(vy);
            else vy = -vy;

            b.hp--;
            if (b.hp <= 0) { destroy(b); it.remove(); }
            else { score += 5; burst(bx, by, b.color, 5); }
        }

        // level cleared
        if (bricks.isEmpty()) {
            level++;
            score += 100;
            buildLevel();
            return;
        }

        // ball lost
        if (by - R > H) {
            lives--;
            superTimer = 0;
            trail.clear();
            if (lives <= 0) {
                state = State.GAME_OVER;
                best = Math.max(best, score);
                pauseMusic();
            } else {
                launched = false;
            }
        }
    }

    void destroy(Brick b) {
        score += 10;
        burst(b.x + BW / 2.0, b.y + BH / 2.0, b.color, 14);
        if (rnd.nextInt(100) < 18) {
            int roll = rnd.nextInt(10);
            int type = roll < 4 ? 0 : (roll < 8 ? 1 : 2);
            powerUps.add(new PowerUp(b.x + BW / 2.0, b.y + BH / 2.0, type));
        }
    }

    void burst(double x, double y, Color c, int n) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double s = 1 + rnd.nextDouble() * 3;
            particles.add(new Particle(x, y, Math.cos(a) * s, Math.sin(a) * s - 1, 25 + rnd.nextInt(15), c));
        }
    }

    void updatePowerUps() {
        for (Iterator<PowerUp> it = powerUps.iterator(); it.hasNext(); ) {
            PowerUp p = it.next();
            p.y += 3;
            if (p.y > H + 20) { it.remove(); continue; }
            if (p.y + 12 >= PADDLE_Y && p.y - 12 <= PADDLE_Y + PADDLE_H
                    && p.x >= paddleX - 12 && p.x <= paddleX + paddleW + 12) {
                switch (p.type) {
                    case 0: superTimer = 420; break;
                    case 1: {
                        double center = paddleX + paddleW / 2;
                        paddleW = WIDE_PADDLE;
                        paddleX = clamp(center - paddleW / 2, 0, W - paddleW);
                        wideTimer = 600;
                        break;
                    }
                    case 2: lives = Math.min(lives + 1, 5); break;
                }
                score += 25;
                burst(p.x, PADDLE_Y, Color.WHITE, 12);
                it.remove();
            }
        }
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---------- drawing ----------
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // background: Park Avenue, NYC
        g.drawImage(cityBg, 0, 0, null);
        drawTaxis(g);
        g.setColor(new Color(0, 0, 0, 45));      // slight darkening so bricks and ball stay readable
        g.fillRect(0, 0, W, H);

        // bricks
        for (Brick b : bricks) {
            g.setColor(b.color);
            g.fillRoundRect(b.x, b.y, BW, BH, 8, 8);
            g.setColor(new Color(255, 255, 255, 70));
            g.fillRoundRect(b.x + 3, b.y + 3, BW - 6, 6, 4, 4);
            if (b.maxHp > 1) {
                g.setColor(b.hp > 1 ? Color.WHITE : new Color(255, 255, 255, 90));
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(b.x, b.y, BW, BH, 8, 8);
            }
        }

        // power-ups
        for (PowerUp p : powerUps) {
            Color c = p.type == 0 ? new Color(255, 140, 0) : p.type == 1 ? new Color(70, 140, 255) : new Color(60, 200, 90);
            String t = p.type == 0 ? "S" : p.type == 1 ? "W" : "+";
            g.setColor(c);
            g.fillOval((int) p.x - 12, (int) p.y - 12, 24, 24);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t, (int) p.x - fm.stringWidth(t) / 2, (int) p.y + 5);
        }

        // particles
        for (Particle p : particles) {
            int alpha = (int) (255.0 * p.life / p.maxLife);
            g.setColor(new Color(p.c.getRed(), p.c.getGreen(), p.c.getBlue(), Math.max(0, Math.min(255, alpha))));
            g.fillOval((int) p.x - 2, (int) p.y - 2, 4, 4);
        }

        // paddle
        g.setPaint(new GradientPaint(0, PADDLE_Y, new Color(120, 220, 255), 0, PADDLE_Y + PADDLE_H, new Color(40, 100, 200)));
        g.fillRoundRect((int) paddleX, PADDLE_Y, (int) paddleW, PADDLE_H, 14, 14);

        // ball trail
        boolean isSuper = superTimer > 0;
        Color ballColor = isSuper ? new Color(255, 150, 20) : new Color(255, 255, 255);
        int i = 0;
        for (double[] t : trail) {
            float a = 0.35f * (1f - i / (float) trail.size());
            g.setColor(new Color(ballColor.getRed(), ballColor.getGreen(), ballColor.getBlue(), (int) (a * 255)));
            int r = Math.max(2, R - i / 2);
            g.fillOval((int) t[0] - r, (int) t[1] - r, r * 2, r * 2);
            i++;
        }

        // ball (with glow in super mode)
        if (isSuper) {
            g.setPaint(new RadialGradientPaint((float) bx, (float) by, 28f, new float[]{0f, 1f},
                    new Color[]{new Color(255, 140, 0, 160), new Color(255, 140, 0, 0)}));
            g.fillOval((int) bx - 28, (int) by - 28, 56, 56);
        }
        g.setPaint(new RadialGradientPaint((float) (bx - 3), (float) (by - 3), R * 1.6f, new float[]{0f, 1f},
                new Color[]{Color.WHITE, isSuper ? new Color(230, 90, 0) : new Color(120, 170, 255)}));
        g.fillOval((int) bx - R, (int) by - R, R * 2, R * 2);

        drawHud(g);

        if (state == State.PLAYING && !launched)
            centered(g, "Click or press SPACE to launch", 22, H / 2 + 90, new Color(255, 255, 255, 200));

        if (state == State.MENU) overlay(g, "SUPER BALL", "Click or press SPACE to start",
                "Move: mouse or \u2190 \u2192  |  Pause: P  |  Music: M  |  Restart: R");
        else if (state == State.PAUSED) overlay(g, "PAUSED", "Press P or click to resume", "");
        else if (state == State.GAME_OVER)
            overlay(g, "GAME OVER", "Score: " + score + "    Best: " + best, "Click or press SPACE to play again");

        if (state == State.MENU) drawCredit(g);
    }

    void drawHud(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.setColor(Color.WHITE);
        g.drawString("Score: " + score, 16, 28);
        String lv = "Level " + level;
        g.drawString(lv, (W - g.getFontMetrics().stringWidth(lv)) / 2, 28);

        // lives as little balls
        for (int i = 0; i < lives; i++) {
            g.setColor(new Color(255, 90, 110));
            g.fillOval(W - 28 - i * 24, 12, 16, 16);
        }

        // power-up timers
        int y = H - 18;
        if (superTimer > 0) bar(g, 16, y, "SUPER", superTimer / 420.0, new Color(255, 140, 0));
        if (wideTimer > 0) bar(g, 200, y, "WIDE", wideTimer / 600.0, new Color(70, 140, 255));
    }

    void bar(Graphics2D g, int x, int y, String label, double frac, Color c) {
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(Color.WHITE);
        g.drawString(label, x, y);
        g.setColor(new Color(255, 255, 255, 50));
        g.fillRoundRect(x + 52, y - 10, 100, 10, 6, 6);
        g.setColor(c);
        g.fillRoundRect(x + 52, y - 10, (int) (100 * frac), 10, 6, 6);
    }

    void overlay(Graphics2D g, String title, String line1, String line2) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, W, H);
        centered(g, title, 64, H / 2 - 30, new Color(255, 200, 60));
        centered(g, line1, 24, H / 2 + 20, Color.WHITE);
        if (!line2.isEmpty()) centered(g, line2, 16, H / 2 + 60, new Color(200, 200, 220));
    }

    void centered(Graphics2D g, String s, int size, int y, Color c) {
        g.setFont(new Font("SansSerif", Font.BOLD, size));
        g.setColor(c);
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    void drawCredit(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(255, 255, 255, 150));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(MUSIC_CREDIT, W - fm.stringWidth(MUSIC_CREDIT) - 10, H - 24);
        g.drawString(MUSIC_SOURCE, W - fm.stringWidth(MUSIC_SOURCE) - 10, H - 10);
        // status so you can see immediately whether the music file was found
        g.setColor(music != null ? new Color(120, 255, 140, 200) : new Color(255, 120, 120, 230));
        g.drawString(musicStatus, W - fm.stringWidth(musicStatus) - 10, H - 38);
    }

    // ---------- NYC / Park Avenue background ----------
    void buildCity() {
        cityBg = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cityBg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Random r = new Random(42);   // fixed seed -> the skyline looks the same every run
        final int GROUND = 520;

        // dusk sky + horizon glow
        g.setPaint(new GradientPaint(0, 0, new Color(8, 10, 34), 0, 440, new Color(125, 52, 92)));
        g.fillRect(0, 0, W, H);
        g.setPaint(new GradientPaint(0, 320, new Color(255, 140, 60, 0), 0, 500, new Color(255, 150, 70, 120)));
        g.fillRect(0, 320, W, 200);

        // stars
        for (int i = 0; i < 70; i++) {
            g.setColor(new Color(255, 255, 255, 60 + r.nextInt(160)));
            g.fillRect(r.nextInt(W), r.nextInt(260), 2, 2);
        }

        // far skyline
        int x = -10;
        while (x < W) {
            int w = 36 + r.nextInt(46), h = 110 + r.nextInt(170);
            g.setColor(new Color(34, 28, 60));
            g.fillRect(x, GROUND - h, w, h);
            windows(g, r, x, GROUND - h, w, h, 0.18, new Color(255, 205, 130, 140));
            x += w + 2;
        }

        // Chrysler Building style tower
        int cx = 590, base = 300;
        g.setColor(new Color(54, 58, 90));
        g.fillRect(cx - 34, base, 68, GROUND - base);
        for (int i = 0; i < 5; i++) {
            int w = 68 - i * 11, h = 26 - i * 3, cy = base - i * 16;
            g.setColor(i % 2 == 0 ? new Color(196, 204, 226) : new Color(132, 142, 176));
            g.fillArc(cx - w / 2, cy - h, w, 2 * h, 0, 180);
            g.setColor(new Color(40, 44, 72));
            for (int k = -w / 2 + 6; k < w / 2 - 4; k += 9) g.fillRect(cx + k, cy - h / 2, 3, 5);
        }
        g.setColor(new Color(215, 222, 240));
        g.fillPolygon(new int[]{cx - 3, cx + 3, cx}, new int[]{base - 80, base - 80, base - 150}, 3);
        windows(g, r, cx - 34, base, 68, GROUND - base, 0.35, new Color(255, 214, 120, 200));

        // near buildings lining the avenue
        x = -20;
        while (x < W) {
            int w = 60 + r.nextInt(50), h = 150 + r.nextInt(150);
            if (x > 520 && x < 680) { x += w + 3; continue; }   // keep the tower visible
            g.setColor(new Color(20, 18, 38));
            g.fillRect(x, GROUND - h, w, h);
            g.setColor(new Color(44, 40, 70));
            g.fillRect(x, GROUND - h, w, 4);
            windows(g, r, x, GROUND - h, w, h, 0.42, new Color(255, 216, 130, 230));
            x += w + 3;
        }

        // sidewalk + road
        g.setColor(new Color(66, 62, 78));
        g.fillRect(0, GROUND, W, 14);
        g.setColor(new Color(90, 86, 104));
        g.fillRect(0, GROUND, W, 3);
        g.setPaint(new GradientPaint(0, GROUND + 14, new Color(38, 38, 50), 0, H, new Color(18, 18, 26)));
        g.fillRect(0, GROUND + 14, W, H - GROUND - 14);
        g.setColor(new Color(240, 200, 60));
        for (int dx = 10; dx < W; dx += 60) g.fillRect(dx, 566, 34, 4);

        // street lamps with glow
        for (int lx : new int[]{190, 340, 470, 720}) {
            g.setColor(new Color(40, 40, 52));
            g.fillRect(lx - 2, GROUND - 90, 4, 90);
            g.fillRect(lx - 2, GROUND - 90, 14, 3);
            g.setPaint(new RadialGradientPaint(lx + 12, GROUND - 86, 46f, new float[]{0f, 1f},
                    new Color[]{new Color(255, 220, 140, 150), new Color(255, 220, 140, 0)}));
            g.fillOval(lx - 34, GROUND - 132, 92, 92);
            g.setColor(new Color(255, 240, 190));
            g.fillOval(lx + 8, GROUND - 90, 8, 5);
        }

        // PARK AVE street sign
        g.setColor(new Color(52, 52, 62));
        g.fillRect(52, GROUND - 100, 4, 100);
        g.setColor(new Color(16, 112, 64));
        g.fillRoundRect(14, GROUND - 124, 82, 26, 6, 6);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(17, GROUND - 121, 76, 20, 4, 4);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        String sign = "PARK AVE";
        g.drawString(sign, 14 + (82 - g.getFontMetrics().stringWidth(sign)) / 2, GROUND - 104);

        g.dispose();
    }

    void windows(Graphics2D g, Random r, int bx0, int by0, int w, int h, double litChance, Color lit) {
        for (int wy = by0 + 10; wy < by0 + h - 8; wy += 14) {
            for (int wx = bx0 + 6; wx < bx0 + w - 8; wx += 11) {
                if (r.nextDouble() < litChance) {
                    g.setColor(lit);
                    g.fillRect(wx, wy, 5, 7);
                }
            }
        }
    }

    void drawTaxis(Graphics2D g) {
        long t = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            int x = (int) (((t * 0.09) + i * 330) % (W + 200)) - 100;
            drawTaxi(g, x, 574);
        }
    }

    void drawTaxi(Graphics2D g, int x, int y) {
        g.setColor(new Color(255, 215, 40));
        g.fillRoundRect(x, y + 6, 52, 12, 8, 8);
        g.fillRoundRect(x + 13, y, 26, 10, 8, 8);
        g.setColor(new Color(40, 50, 70));
        g.fillRect(x + 16, y + 2, 9, 6);
        g.fillRect(x + 27, y + 2, 9, 6);
        g.setColor(new Color(255, 240, 160));
        g.fillRect(x + 22, y - 3, 8, 3);                  // roof light
        g.setColor(new Color(20, 20, 24));
        g.fillOval(x + 7, y + 13, 10, 10);
        g.fillOval(x + 35, y + 13, 10, 10);
        g.setColor(new Color(255, 255, 210, 220));
        g.fillRect(x + 50, y + 9, 3, 4);                  // headlight
        g.setPaint(new GradientPaint(x + 52, 0, new Color(255, 255, 200, 90), x + 82, 0, new Color(255, 255, 200, 0)));
        g.fillRect(x + 52, y + 7, 30, 8);
    }

    // ---------- entry point ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Super Ball");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setResizable(false);
            SuperBall game = new SuperBall();
            f.add(game);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
