/**
 * Práctica de Redes de Computadores - 2019/2020
 *
 * Joan Vallori
 * Izar Maria Pietro Castorina
 * Lisandro Rocha
 *
 */
package clientxat;

import java.awt.event.KeyEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;

public class ClientXat extends javax.swing.JFrame {

    static Socket S;
    static DataOutputStream DOS;
    static DataInputStream DIS;

    static volatile boolean done = false;   // Booleano para la espera activa

    static ClientXat c;

    // Constructor del cliente
    public ClientXat() {
        setTitle("Client");
        this.setResizable(false);
        initComponents();
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jTextField1 = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jTextArea1.setColumns(20);
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setTabSize(4);
        jScrollPane1.setViewportView(jTextArea1);

        jTextField1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextField1KeyReleased(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTextField1)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 260, Short.MAX_VALUE)
                .addGap(7, 7, 7)
                .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>                        

    private void jTextField1KeyReleased(java.awt.event.KeyEvent evt) {                                        
        // Envío del mensaje al pulsar la tecla "Intro"
        
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            try {
                String msgout = "";
                String encr = "";
                // Se genera el mensaje encriptado
                msgout = jTextField1.getText().trim();
                encr = encripta(msgout);

                //System.out.println("DEBUG - mensaje:    " + msgout);
                //System.out.println("DEBUG - encriptado: " + encr);
                DOS.writeUTF(encr);
                jTextField1.setText("");
                jTextArea1.append("Yo:\t" + msgout + "\n");
                // Scroll de la jTextArea para que sea siempre visible el último mensaje recibido
                jTextArea1.setCaretPosition(jTextArea1.getText().length() - 1);
            } catch (IOException ex) {
                Logger.getLogger(ClientXat.class.getName()).log(Level.SEVERE, null, ex);
            }

            // Si ha sido pulsada la tecla "Esc", se cierra la conexión y se sale del client
        } else if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
            jTextArea1.append("**Saliendo de la aplicación (Tecla ESC)**");
            jTextArea1.update(jTextArea1.getGraphics());  // Actualiza la textArea
            desconectar();            
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ClientXat.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.exit(0);
        }
    }                                       

    // Thread principal
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                c = new ClientXat();
                c.setVisible(true);
                done = true;
            }
        });

        try {
            // Espera activa a que el cliente se inicialice
            while (!done) {
            }

            // Creación del socket del cliente, conectado al puerto del servidor 9090
            // El IP "0.0.0.0" indica que la búsqueda del servidor se hace en
            // todas las NICs disponibles
            S = new Socket("0.0.0.0", 9090);
            System.out.println("Cliente " + S.getLocalSocketAddress() + " - Socket creado.");

            // Cambio el título de la ventana con los datos del cliente
            c.setTitle("Client " + S.getLocalSocketAddress().toString().substring(S.getLocalSocketAddress().toString().indexOf(":") + 1, S.getLocalSocketAddress().toString().length()));

            // Generación de los buffers de E/S
            DIS = new DataInputStream(S.getInputStream());
            DOS = new DataOutputStream(S.getOutputStream());
            String msgin = "";
            String desenc = "";

            // Hasta que no se reciba el mensaje de exit
            while (!desenc.equals("exit")) {
                try {
                    msgin = DIS.readUTF();
                } catch (SocketException ex) {
                    jTextArea1.append("**Conexión cerrada (Tecla ESC)**");
                    break;
                }
                desenc = decripta(msgin);
                jTextArea1.append("Server:\t" + desenc + "\n");
                // Scroll de la jTextArea para que sea siempre visible el último mensaje recibido
                jTextArea1.setCaretPosition(jTextArea1.getText().length() - 1);
            }
            jTextArea1.append("**Recibido mensaje de exit desde el servidor.**");
            desconectar();
        } catch (EOFException e) {
            System.out.println("Conexión terminada.");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    private static void desconectar() {
        try {
                System.out.println("Cerrando la conexión");
                if (S.isConnected()) {
                    System.out.println("DEBUG - Enviando comando de cierre");
                    DIS.close();
                    DOS.close();
                    S.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(ClientXat.class.getName()).log(Level.SEVERE, null, ex);
            }
    }

    // Función básica de encriptación
    // Mensaje -> ASCII -> Hexadecimal de cada carácter
    public String encripta(String mensaje) {
        String encr = "";

        for (int i = 0; i < mensaje.length(); i++) {
            encr += Integer.toHexString((int) mensaje.charAt(i));
            encr += " ";
        }
        System.out.println("DEBUG - encr: " + encr);
        encr = encr.trim();
        return encr;
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

    // Variables declaration - do not modify                     
    private javax.swing.JScrollPane jScrollPane1;
    private static javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextField jTextField1;
    // End of variables declaration                   
}
