/**
 * Práctica de Redes de Computadores - 2019/2020
 *
 * Joan Albert Vallori Aguiló
 * Izar Maria Pietro Castorina
 * Lisandro Rocha
 *
 */
package serverxat;

import java.awt.event.KeyEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;

public class ServerXat extends javax.swing.JFrame {

    static ServerSocket SS;
    static Socket S;

    static Thread acceptClients;    // Permite a varios clientes de conectarse
    static Thread readInputs;       // Permite recibir mensajes desde los clientes
    static Thread checkOnline;      // Comprueba si los clientes están online

    // Sockets y buffers de entrada/salida de mensajes
    static ArrayList<Socket> clients = new ArrayList<Socket>();
    static ArrayList<DataInputStream> DIS_AL = new ArrayList<DataInputStream>();
    static ArrayList<DataOutputStream> DOS_AL = new ArrayList<DataOutputStream>();

    static ArrayList<Thread> readInputs_AL = new ArrayList<Thread>();

    // Lista de contadores de tiempo para cada cliente conectado
    static ArrayList<Integer> timeouts = new ArrayList<Integer>();

    static String msgin = "";
    static String desenc = "";
    static int selected = 0;    // Cliente seleccionado en el jComboBox
    static int toClose = -1;    // Índice del cliente a desconectar

    // Variables volátiles (necesitan ser leídas directamente desde memoria
    // a causa de la concurrencia entre los varios threads)
    static volatile int connected = 0;  // Número de clientes conectados
    static volatile boolean done = false;   // Booleano para la espera activa

    static TimerTask repeatedTask;      // Tarea para contadores de timeout
    static final int TOUT = 60;         // Tiempo en segundos que tiene que pasar
    // desde la recepción del último mensaje para
    // que el usuario sea desconectado

    // Constructor del servidor
    public ServerXat() {
        initThreads();
        setTitle("Server");
        initComponents();
    }

    // Inicializa los threads
    public void initThreads() {
        // Permite a los clientes de conectarse cuando quieran
        acceptClients = new Thread() {
            @Override
            public void run() {
                try {
                    while (true) {  // Bucle infinito
                        // Se añade el cliente a la lista
                        clients.add(SS.accept());
                        System.out.println("Remote add: " + clients.get(clients.size() - 1).getRemoteSocketAddress().toString());
                        jTextArea1.append("**Cliente " + clients.get(clients.size() - 1).getRemoteSocketAddress().toString() + " se ha conectado.**\n");

                        // Scroll de la jTextArea para que sea siempre visible el último mensaje recibido
                        jTextArea1.setCaretPosition(jTextArea1.getText().length() - 1);

                        // Creación de buffers E/S para el nuevo cliente
                        DOS_AL.add(new DataOutputStream(clients.get(clients.size() - 1).getOutputStream()));
                        DIS_AL.add(new DataInputStream(clients.get(clients.size() - 1).getInputStream()));

                        timeouts.add(0);    // Nuevo timeout para el cliente a 0

                        //System.out.println("clients: " + clients.size() + " | DOS_AL: " + DOS_AL.size()
                        //        + " | DIS_AL: " + DIS_AL.size());
                        // Actualización de la cantidad de clientes conectados
                        connected = clients.size();

                        // Se añade el nuevo cliente al jComboBox para poderlo seleccionar
                        jComboBox1.addItem(clients.get(clients.size() - 1).getRemoteSocketAddress().toString().substring(clients.get(clients.size() - 1).getRemoteSocketAddress().toString().indexOf(":") + 1, clients.get(clients.size() - 1).getRemoteSocketAddress().toString().length()));

                    }
                } catch (IOException ex) {
                    Logger.getLogger(ServerXat.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };

        // Permite recibir mensajes desde cada cliente
        readInputs = new Thread() {
            @Override
            public void run() {
                try {
                    while (true) {  // Bucle infinito

                        for (int i = 0; i < connected; i++) {   // Para cada cliente conectado

                            if (DIS_AL.get(i).available() > 0) { // Si hay algo para leer en el buffer
                                try {
                                    msgin = DIS_AL.get(i).readUTF();

                                } catch (IOException e) { // Si hay un error al leer, el cliente muy probablemente será offline
                                    System.out.println("DEBUG - Imposible leer, marcando el socket como cerrado");
                                    // Si el socket del cliente no ha sido marcado ya como cerrado, entonces se marca
                                    // Comprobación necesaria porque también puede cerrarse el socket por timeout
                                    if (!clients.get(i).isClosed()) {
                                        clients.get(i).close();
                                    }
                                    break;  // Se cancelan las otras operaciones del bucle
                                }

                                // Reset del timeout porque se ha recibido un mensaje
                                timeouts.set(i, 0);

                                // Decriptación del mensaje recibido y visualización del mensaje
                                desenc = decripta(msgin);
                                jTextArea1.append("Cliente" + clients.get(i).getRemoteSocketAddress().toString().substring(clients.get(i).getRemoteSocketAddress().toString().indexOf(":") + 1, clients.get(i).getRemoteSocketAddress().toString().length()) + ":\t" + desenc + "\n");

                                // Scroll de la jTextArea para que sea siempre visible el último mensaje recibido
                                jTextArea1.setCaretPosition(jTextArea1.getText().length() - 1);

                                jTextArea1.update(jTextArea1.getGraphics());  // Actualiza la textArea
                            }
                        }
                        Thread.sleep(150);  // Para evitar de sobrecargar la CPU
                    }
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(ServerXat.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };

        // Comprobación (cada 2 segundos) del estado (online/offline) de cada cliente
        checkOnline = new Thread() {
            @Override
            public void run() {
                try {
                    while (true) {  // Bucle infinito
                        for (int i = 0; i < connected; i++) {
                            // El cliente está online si su socket no está cerrado
                            boolean online = !clients.get(i).isClosed();
                            if (!online) {
                                System.out.println("DEBUG - socket n." + i + " está offline!");
                                desconecta(i);
                            }
                        }
                        Thread.sleep(2000);
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(ServerXat.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };

        // Tarea para actualizar los timeouts de cada cliente cada segundo
        repeatedTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    timekeep();
                } catch (IOException ex) {
                    Logger.getLogger(ServerXat.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        new Timer().schedule(repeatedTask, new Date(), 1000);
        System.out.println("DEBUG - Timekeeping has started");

        System.out.println("DEBUG - Threads inicializados.");

        done = true;    // Indica al main thread que puede seguir con las operaciones
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jTextField1 = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jComboBox1 = new javax.swing.JComboBox<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jTextArea1.setColumns(20);
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setTabSize(4);
        jScrollPane1.setViewportView(jTextArea1);

        jTextField1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTextField1KeyPressed(evt);
            }
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextField1KeyReleased(evt);
            }
        });

        jLabel1.setText("Enviar a");

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] {"Todos"}));
        jComboBox1.setSelectedIndex(0);
        jComboBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 228, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jComboBox1, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox1ActionPerformed
        // Se actualiza la variable "selected" según lo que el usuario selecciona
        selected = jComboBox1.getSelectedIndex();
    }//GEN-LAST:event_jComboBox1ActionPerformed

    private void jTextField1KeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextField1KeyPressed

    }//GEN-LAST:event_jTextField1KeyPressed

    private void jTextField1KeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextField1KeyReleased
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            String msg = encripta(jTextField1.getText().trim());
            // Enviar a todos
            if (selected == 0) {
                System.out.println("DEBUG - Enviando a todos");
                for (int i = 0; i < connected; i++) {
                    try {
                        DOS_AL.get(i).writeUTF(msg);
                    } catch (IOException ex) {
                        Logger.getLogger(ServerXat.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                msg = decripta(msg);
                jTextArea1.append("Server@Todos:\t" + msg + "\n");
                jTextField1.setText("");
            } else if (selected != 0 && selected <= connected) {    // Enviar solo a un cliente
                System.out.println("DEBUG - Enviando solo a " + clients.get(selected - 1).getRemoteSocketAddress().toString());
                try {
                    DOS_AL.get(selected - 1).writeUTF(msg);
                    jTextField1.setText("");
                    msg = decripta(msg);
                    jTextArea1.append("Server@"
                            + clients.get(selected - 1).getRemoteSocketAddress().toString().substring(clients.get(selected - 1).getRemoteSocketAddress().toString().indexOf(":") + 1, clients.get(selected - 1).getRemoteSocketAddress().toString().length())
                            + ":\t" + msg + "\n");
                } catch (IOException ex) {
                    Logger.getLogger(ServerXat.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        // Scroll de la jTextArea para que sea siempre visible el último mensaje recibido
        jTextArea1.setCaretPosition(jTextArea1.getText().length() - 1);
        jTextArea1.update(jTextArea1.getGraphics());  // Actualiza la textArea
    }//GEN-LAST:event_jTextField1KeyReleased

    // Thread principal
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ServerXat().setVisible(true);
            }
        });

        try {
            // Espera activa a que los threads se inicializen
            while (!done) {
            }

            // Creación del socket de servidor en el puerto 9090
            SS = new ServerSocket(9090, 0, InetAddress.getByName("192.168.1.199"));
            System.out.println("Server socket address: " + SS.getLocalSocketAddress().toString());
            System.out.println("Servidor activo en el puerto 9090.");

            acceptClients.start();
            System.out.println("DEBUG - AcceptClients iniciado, esperando a los clientes");

            readInputs.start();
            System.out.println("DEBUG - ReadInputs iniciado, esperando a recibir mensajes");

            checkOnline.start();
            System.out.println("DEBUG - CheckOnline iniciado, comprobando el estado de los clientes");

        } catch (BindException e) {
            System.err.println("ERROR - Ya hay un proceso servidor en ejecución! Cerrando...");
            System.exit(1);
        } catch (EOFException e) {
            System.out.println("Cierre del servidor.");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // Función de desencriptación dado un array de números hexadecimales
    // Vuelve a convertir la secuencia de enteros hex. en carácteres
    public static String decripta(String mensaje) {
        ArrayList<String> chars = new ArrayList<>();
        String curr = "";
        for (int i = 0; i < mensaje.length(); i++) {
            if (mensaje.charAt(i) != ' ') {
                curr += mensaje.charAt(i);
            } else {
                chars.add(curr);
                curr = "";
            }
        }
        chars.add(curr);
        String orig = "";
        orig = chars.stream().map((str) -> new String(DatatypeConverter.parseHexBinary(str))).reduce(orig, String::concat);
        return orig;
    }

    // Función básica de encriptación
    // Mensaje -> ASCII -> Hexadecimal de cada carácter
    public String encripta(String mensaje) {
        String encr = "";

        for (int i = 0; i < mensaje.length(); i++) {
            encr += Integer.toHexString((int) mensaje.charAt(i));
            encr += " ";
        }
        encr = encr.trim();
        return encr;
    }

    // Elimina un cliente desde todos los arrays
    private void desconecta(int i) {
        jTextArea1.append("**Cliente " + clients.get(i).getRemoteSocketAddress().toString() + " se ha desconectado.**\n");
        jTextArea1.setCaretPosition(jTextArea1.getText().length() - 1);
        jTextArea1.update(jTextArea1.getGraphics());  // Actualiza la textArea

        jComboBox1.removeItem(clients.get(clients.size() - 1).getRemoteSocketAddress().toString().substring(clients.get(clients.size() - 1).getRemoteSocketAddress().toString().indexOf(":") + 1, clients.get(clients.size() - 1).getRemoteSocketAddress().toString().length()));

        clients.remove(i);
        DOS_AL.remove(i);
        DIS_AL.remove(i);
        timeouts.remove(i);
        connected--;
    }

    // Actualiza los timeouts actuales de cada cliente
    public void timekeep() throws IOException {
        for (int i = 0; i < connected; i++) {
            timeouts.set(i, timeouts.get(i) + 1);   // Incremento +1

            // Si faltan 10 segundos o menos al timeout
            if (timeouts.get(i) > TOUT - 10 && timeouts.get(i) <= TOUT) {
                System.out.println("**Cliente " + clients.get(i).getRemoteSocketAddress().toString() + ": "
                        + (TOUT - timeouts.get(i)) + " segundos al timeout!**");
            } // Si el valor de timeout ha sido sobrepasado
            else if (timeouts.get(i) > TOUT) {
                String client = clients.get(i).getRemoteSocketAddress().toString().substring(clients.get(i).getRemoteSocketAddress().toString().indexOf(":") + 1, clients.get(i).getRemoteSocketAddress().toString().length());
                System.err.println("**El cliente " + client + " ha excedido el tiempo límite preestablecido de " + TOUT + " segundos.**");

                DOS_AL.get(i).writeUTF("**Iniciando la desconexión por timeout de inactividad (" + TOUT + " segundos).**");
                clients.get(i).close();
                desconecta(i);
            }
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private static javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextField jTextField1;
    // End of variables declaration//GEN-END:variables
}
