package org.demo1;// Juego de Laberinto de Halloween — pantalla completa, movimiento fluido y jumpscare final con sonido
// Listo para IntelliJ (JDK 11+). Un solo archivo.
// Controles: Flechas para moverte, ESC para salir. Llega a la meta antes de que termine el tiempo.
// Al ganar o perder: se muestra "jumpscare.png" a pantalla completa, suena "jumpscare.wav" y, tras 4 s, vuelve al menú.
// Coloca los archivos en:
//   - Como recursos (recomendado para .jar): src/main/resources/  (puedes usar subcarpetas). Marca como Resources Root.
//   - O como archivos externos en la carpeta raíz del proyecto.
// El cargador intentará rutas: "/jumpscare.png", "/org/demo1/jumpscare.png", "/org.demo1/jumpscare.png" y luego archivo externo "jumpscare.png".
// Para el sonido intentará: "/jumpscare.wav", "/org/demo1/jumpscare.wav", "/org.demo1/jumpscare.wav" y luego archivo externo "jumpscare.wav".

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.sound.sampled.*;
import java.io.*;

public class HalloweenMaze extends JFrame {
    private static final int TIMER_SECONDS = 30;        // tiempo total
    private static final int AUTO_RESET_MS = 4000;      // reinicio auto
    private static final String SCARY_IMAGE_FILE = "jumpscare.png"; // nombre del archivo externo
    private static final String SCARE_SOUND_FILE = "jumpscare.wav"; // usa WAV (PCM)

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final MenuPanel menuPanel = new MenuPanel();
    private final GamePanel gamePanel = new GamePanel();
    private final EndPanel endPanel  = new EndPanel();

    public HalloweenMaze() {
        super("Laberinto de Halloween");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setUndecorated(true);                       // pantalla completa sin bordes
        setExtendedState(JFrame.MAXIMIZED_BOTH);    // maximizar

        root.add(menuPanel, "MENU");
        root.add(gamePanel, "GAME");
        root.add(endPanel,  "END");
        setContentPane(root);

        // ESC global para salir
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0), "exit");
        root.getActionMap().put("exit", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){
                gamePanel.stopAll();
                System.exit(0);
            }
        });
    }

    private void startGame() {
        gamePanel.start();
        cards.show(root, "GAME");
        gamePanel.requestFocusInWindow();
    }

    private void showEnd(boolean win) {
        endPanel.showResult(win);
        cards.show(root, "END");
        new Timer(AUTO_RESET_MS, e -> {
            ((Timer) e.getSource()).stop();
            gamePanel.resetState();
            cards.show(root, "MENU");
            menuPanel.restartAnim();
        }).start();
    }

    // ====== Panel del Menú con efecto de sangre ======
    private class MenuPanel extends JPanel {
        private final Timer anim;
        private final List<Drop> drops = new ArrayList<>();
        private final Random rnd = new Random();

        MenuPanel() {
            setBackground(Color.BLACK);
            setFocusable(true);
            setLayout(new GridBagLayout());

            JButton start = new JButton("EMPEZAR");
            start.setFont(start.getFont().deriveFont(Font.BOLD, 28f));
            start.addActionListener(e -> startGame());

            JButton exit = new JButton("Salir (Esc)");
            exit.addActionListener(e -> {
                gamePanel.stopAll();
                System.exit(0);
            });

            JPanel box = new JPanel();
            box.setOpaque(false);
            box.setLayout(new GridLayout(0,1,10,10));
            JLabel title = new JLabel("NOCHE DEL LABERINTO", SwingConstants.CENTER);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 42f));
            title.setForeground(new Color(255, 80, 80));
            box.add(title);
            box.add(start);
            box.add(exit);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10,10,10,10);
            add(box, gbc);

            // Gotas de sangre animadas
            anim = new Timer(30, e -> {
                if (rnd.nextFloat() < 0.08f) {
                    drops.add(new Drop(rnd.nextInt(Math.max(1, getWidth())), 0, 2 + rnd.nextInt(6)));
                }
                for (Drop d : drops) d.update();
                drops.removeIf(d -> d.y > getHeight()+20);
                repaint();
            });
            anim.start();
        }

        void restartAnim(){ if(!anim.isRunning()) anim.start(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setPaint(new GradientPaint(0,0,new Color(10,10,10),0,h,new Color(0,0,0)));
            g2.fillRect(0,0,w,h);
            g2.setColor(new Color(120, 0, 0, 180));
            for (int i=0;i<w;i+=120) {
                g2.fillOval(i-40, 0, 120, 60);
                g2.fillRect(i, 30, 20, 80 + (i%60));
            }
            g2.setColor(new Color(150, 0, 0, 200));
            for (Drop d : drops) d.draw(g2);
        }

        class Drop { int x,y,speed; Drop(int x,int y,int s){this.x=x;this.y=y;this.speed=s;} void update(){ y+=speed; } void draw(Graphics2D g){ g.fillOval(x,y,6,10);} }
    }

    // ====== Panel del Juego con movimiento suave ======
    private class GamePanel extends JPanel {
        // 25x15 (1=pared, 0=camino). Entrada (1,1) y salida (23,13)
        final int[][] MAZE = {
                {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
                {1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1},
                {1,0,1,0,1,0,1,1,1,1,0,1,0,0,1,0,1,0,1,0,1,1,1,0,1},
                {1,0,1,0,0,0,0,0,1,0,0,0,0,1,1,0,1,0,0,0,0,0,1,0,1},
                {1,0,1,1,1,1,1,0,1,1,1,0,1,0,1,0,1,1,1,1,1,0,1,0,1},
                {1,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,1,0,1,0,1},
                {1,1,1,1,1,0,1,1,1,0,1,0,1,1,1,1,1,0,1,0,1,0,1,0,1},
                {1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1},
                {1,0,1,0,1,1,1,0,1,1,1,1,1,1,1,0,1,1,1,0,1,1,1,0,1},
                {1,0,1,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1},
                {1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,0,1,1,1,1,1,0,1},
                {1,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,1},
                {1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,0,1,0,1},
                {1,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1},
                {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
        };

        final int COLS = MAZE[0].length;
        final int ROWS = MAZE.length;
        final int HUD_H = 60;
        int CELL_SIZE;

        // jugador con movimiento suave
        double x, y;            // centro del jugador en píxeles
        double vx = 0, vy = 0;  // velocidad en píxeles/frame
        final double SPEED = 4; // 4 px por tick (~60 FPS)
        final int R = 12;       // radio del jugador

        final int goalX = 23, goalY = 13; // en celdas

        int timeLeft = TIMER_SECONDS;
        final Timer countdown;   // inicializado en el constructor
        final Timer gameLoop;    // inicializado en el constructor

        // sonido ambiente (opcional) y grito final
        Clip ambientClip;
        Clip scareClip;

        GamePanel() {
            setBackground(Color.BLACK);
            setFocusable(true);

            // Timers en el constructor
            countdown = new Timer(1000, e -> {
                timeLeft--;
                if (timeLeft <= 0) {
                    stopAll();
                    showEnd(false);
                }
                repaint();
            });

            gameLoop = new Timer(16, e -> { // ~60fps
                double nx = x + vx;
                double ny = y + vy;

                if (!collides((int)(nx - R), (int)(y - R)) && !collides((int)(nx + R - 1), (int)(y - R))
                        && !collides((int)(nx - R), (int)(y + R - 1)) && !collides((int)(nx + R - 1), (int)(y + R - 1))) {
                    x = nx;
                } else { vx = 0; }

                nx = x; ny = y + vy;
                if (!collides((int)(nx - R), (int)(ny - R)) && !collides((int)(nx + R - 1), (int)(ny - R))
                        && !collides((int)(nx - R), (int)(ny + R - 1)) && !collides((int)(nx + R - 1), (int)(ny + R - 1))) {
                    y = ny;
                } else { vy = 0; }

                int cx = (int) (x / CELL_SIZE);
                int cy = (int) (y / CELL_SIZE);
                if (cx == goalX && cy == goalY) {
                    stopAll();
                    showEnd(true);
                }
                repaint();
            });

            // Key Bindings para movimiento fluido
            bind(KeyEvent.VK_LEFT, true,  () -> vx = -SPEED);
            bind(KeyEvent.VK_RIGHT, true, () -> vx =  SPEED);
            bind(KeyEvent.VK_UP, true,    () -> vy = -SPEED);
            bind(KeyEvent.VK_DOWN, true,  () -> vy =  SPEED);

            bind(KeyEvent.VK_LEFT, false,  () -> { if (vx < 0) vx = 0; });
            bind(KeyEvent.VK_RIGHT, false, () -> { if (vy > 0) vy = 0; });
            bind(KeyEvent.VK_UP, false,    () -> { if (vy < 0) vy = 0; });
            bind(KeyEvent.VK_DOWN, false,  () -> { if (vy > 0) vy = 0; });

            addComponentListener(new ComponentListener() {
                @Override public void componentResized(ComponentEvent e) {
                    int w = getWidth();
                    int h = getHeight() - HUD_H;
                    CELL_SIZE = Math.max(16, Math.min(Math.max(1,w) / COLS, Math.max(1,h) / ROWS));
                }
                @Override public void componentMoved(ComponentEvent e) {}
                @Override public void componentShown(ComponentEvent e) {}
                @Override public void componentHidden(ComponentEvent e) {}
            });

            // cargar audio
            ambientClip = loadClip(new String[]{
                    "/ambience.wav", "/org/demo1/ambience.wav", "/org.demo1/ambience.wav"}, "ambience.wav");
            scareClip   = loadClip(new String[]{
                    "/"+SCARE_SOUND_FILE, "/org/demo1/"+SCARE_SOUND_FILE, "/org.demo1/"+SCARE_SOUND_FILE}, SCARE_SOUND_FILE);
        }

        void bind(int key, boolean pressed, Runnable r){
            String name = key + (pressed?"_P":"_R");
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, !pressed), name);
            getActionMap().put(name, new AbstractAction(){
                @Override public void actionPerformed(ActionEvent e){ r.run(); }
            });
        }

        void start() {
            resetState();
            countdown.start();
            gameLoop.start();
            if (ambientClip != null) {
                ambientClip.setFramePosition(0);
                ambientClip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        }

        void stopAll(){
            if (gameLoop.isRunning()) gameLoop.stop();
            if (countdown.isRunning()) countdown.stop();
            if (ambientClip != null && ambientClip.isRunning()) ambientClip.stop();
        }

        void resetState() {
            stopAll();
            int w = getWidth();
            int h = getHeight() - HUD_H;
            CELL_SIZE = Math.max(16, Math.min(Math.max(1,w) / COLS, Math.max(1,h) / ROWS));
            x = (1 * CELL_SIZE) + CELL_SIZE/2.0;
            y = (1 * CELL_SIZE) + CELL_SIZE/2.0;
            vx = vy = 0;
            timeLeft = TIMER_SECONDS;
            requestFocusInWindow();
            repaint();
        }

        private boolean collides(int px, int py){
            int cx = px / CELL_SIZE;
            int cy = py / CELL_SIZE;
            if (cx < 0 || cy < 0 || cx >= COLS || cy >= ROWS) return true;
            return MAZE[cy][cx] == 1;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            float alpha = 0.85f + (float)(Math.sin(System.nanoTime()*1e-9*8)*0.08);
            g2.setComposite(AlphaComposite.SrcOver.derive(1f));
            g2.setPaint(new GradientPaint(0,0,new Color(8,8,8),0,h,new Color(0,0,0)));
            g2.fillRect(0,0,w,h);

            for (int r=0;r<ROWS;r++){
                for(int c=0;c<COLS;c++){
                    int px = c*CELL_SIZE;
                    int py = r*CELL_SIZE;
                    if (MAZE[r][c]==1){
                        g2.setColor(new Color(25,25,25));
                        g2.fillRect(px,py,CELL_SIZE,CELL_SIZE);
                        g2.setColor(new Color(60,0,0,140));
                        g2.drawRect(px,py,CELL_SIZE, CELL_SIZE);
                    } else {
                        g2.setColor(new Color(12,12,12));
                        g2.fillRect(px,py,CELL_SIZE,CELL_SIZE);
                    }
                }
            }

            g2.setColor(new Color(255, 120, 0));
            g2.fillRect(goalX*CELL_SIZE+6, goalY*CELL_SIZE+6, CELL_SIZE-12, CELL_SIZE-12);

            int px = (int)Math.round(x);
            int py = (int)Math.round(y);
            g2.setColor(new Color(20,20,20));
            g2.fillOval(px-R-2, py-R-2, (R+2)*2, (R+2)*2);
            g2.setColor(new Color(230,230,230));
            g2.fillOval(px-R, py-R, R*2, R*2);
            g2.setColor(Color.BLACK);
            g2.fillOval(px-6, py-6, 4, 6);
            g2.fillOval(px+2, py-6, 4, 6);
            g2.drawArc(px-8, py+2, 16, 10, 20, 140);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            Polygon cone = new Polygon();
            cone.addPoint(px, py);
            cone.addPoint(px + (int)(R*4), py - (int)(R*1.2));
            cone.addPoint(px + (int)(R*4), py + (int)(R*1.2));
            g2.setColor(new Color(255, 180, 80, 120));
            g2.fillPolygon(cone);
            g2.setComposite(AlphaComposite.SrcOver);

            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            g2.drawString("Tiempo: " + timeLeft + "s", 16, h - HUD_H + 24);
            g2.drawString("Meta: casilla naranja", 16, h - HUD_H + 48);
        }

        private Clip loadClip(String[] resourcePaths, String fallbackFile){
            for(String p: resourcePaths){
                try{
                    java.net.URL url = getClass().getResource(p);
                    if(url!=null){
                        AudioInputStream ais = AudioSystem.getAudioInputStream(url);
                        Clip c = AudioSystem.getClip();
                        c.open(ais);
                        System.out.println("Audio cargado desde recursos: "+p);
                        return c;
                    }
                }catch(Exception ignored){}
            }
            // Fallback a archivo externo
            try{
                File f = new File(fallbackFile);
                if(f.exists()){
                    AudioInputStream ais = AudioSystem.getAudioInputStream(f);
                    Clip c = AudioSystem.getClip();
                    c.open(ais);
                    System.out.println("Audio cargado desde archivo: "+fallbackFile);
                    return c;
                }
            }catch(Exception ex){ System.err.println("No se pudo cargar audio: "+ex.getMessage()); }
            return null;
        }
    }

    // ====== Pantalla final con imagen a pantalla completa (jumpscare) ======
    private class EndPanel extends JPanel {
        private boolean win = false;
        private Image scaryImage;
        private String message = "";
        private float flashAlpha = 0f;
        private final Timer flashTimer;

        EndPanel(){
            setBackground(Color.BLACK);
            ensureImageLoaded();
            flashTimer = new Timer(40, e -> {
                flashAlpha = (float)Math.max(0, Math.min(1, flashAlpha + 0.12f*(flashDir?1:-1)));
                if (flashAlpha >= 1f) flashDir = false;
                if (flashAlpha <= 0f) ((Timer)e.getSource()).stop();
                repaint();
            });
        }
        boolean flashDir = true;

        private void ensureImageLoaded(){
            String[] resourceCandidates = new String[]{
                    "/jumpscare.png", "/org/demo1/jumpscare.png", "/org.demo1/jumpscare.png"
            };
            for(String p: resourceCandidates){
                try{
                    java.net.URL url = getClass().getResource(p);
                    if (url != null) {
                        scaryImage = new ImageIcon(url).getImage();
                        System.out.println("Cargada desde recursos: " + p);
                        return;
                    }
                }catch(Exception ignored){}
            }
            try {
                ImageIcon icon = new ImageIcon(SCARY_IMAGE_FILE);
                scaryImage = icon.getImage();
                System.out.println("Cargada desde archivo: " + SCARY_IMAGE_FILE);
            } catch (Exception e) {
                scaryImage = null;
                System.err.println("No se pudo cargar la imagen: " + e.getMessage());
            }
        }

        void showResult(boolean win){
            ensureImageLoaded();
            this.win = win;
            this.flashAlpha = 0f; this.flashDir = true;
            message = win ? "¡ESCAPASTE!" : "SE ACABÓ EL TIEMPO";
            // sonido de susto
            try{
                Clip sc = gamePanel.scareClip;
                if(sc!=null){ sc.stop(); sc.setFramePosition(0); sc.start(); }
            }catch(Exception ignored){}
            flashTimer.start();
            repaint();
        }

        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (scaryImage != null){
                int iw = scaryImage.getWidth(this);
                int ih = scaryImage.getHeight(this);
                if (iw>0 && ih>0){
                    double scale = Math.max(w/(double)iw, (h)/(double)ih);
                    int sw = (int)Math.round(iw*scale);
                    int sh = (int)Math.round(ih*scale);
                    int ox = (w - sw)/2;
                    int oy = (h - sh)/2;
                    g2.drawImage(scaryImage, ox, oy, sw, sh, this);
                }
            } else {
                g2.setColor(Color.BLACK);
                g2.fillRect(0,0,w,h);
                g2.setColor(Color.RED);
                g2.setFont(getFont().deriveFont(Font.BOLD, 28f));
                g2.drawString("No se encontró 'jumpscare.png'", 40, 80);
            }

            RadialGradientPaint vignette = new RadialGradientPaint(
                    new Point(w/2, h/2), Math.max(w,h)/1.2f,
                    new float[]{0f, 1f}, new Color[]{new Color(0,0,0,0), new Color(0,0,0,200)});
            g2.setPaint(vignette);
            g2.fillRect(0,0,w,h);

            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, 48f));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(message);
            g2.drawString(message, (w - tw)/2, Math.max(80, h/6));

            g2.setComposite(AlphaComposite.SrcOver.derive(flashAlpha));
            g2.setColor(Color.WHITE);
            g2.fillRect(0,0,w,h);
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            HalloweenMaze frame = new HalloweenMaze();
            frame.setVisible(true);
        });
    }
}
