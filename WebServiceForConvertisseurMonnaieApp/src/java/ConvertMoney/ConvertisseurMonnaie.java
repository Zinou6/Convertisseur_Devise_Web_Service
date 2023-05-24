package ConvertMoney;

import com.itextpdf.text.DocumentException;
import java.awt.Desktop;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.swing.JOptionPane;
import org.jsoup.Jsoup;

/**
 *
 * @author HP
 */

@WebService
public class ConvertisseurMonnaie {
    
    LocalDate today = LocalDate.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    String todayString  = today.format(formatter);
    
    static String DBurl = "jdbc:mysql://localhost:3300/conversion_db?zeroDateTimeBehavior=convertToNull";
    static Connection conn = null;
    
    static org.jsoup.nodes.Document doc = null;
    
    static double LeCours = 0;
    static String DateCours = "";
    
    

    @WebMethod
    public boolean connectionNetwork(){
        try {
            String urlCurrency = "https://www.google.com/search?q=dzd+to+euro&oq=dzd+to+euro&aqs=chrome..69i57j0i512j0i22i30l5j69i60.14419j1j7&sourceid=chrome&ie=UTF-8";
            doc = Jsoup.connect(urlCurrency).get();
            return true;
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            return false;
        }
    }
    
    public void connection (){
        try{
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DBurl,"root","");
        } catch (ClassNotFoundException ex) {
            System.out.println(ex.getMessage());
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }
    
    public void networkCoursORlastCours(){
        if(connectionNetwork()){
            String exchangeRateStr = doc.select("#knowledge-currency__updatable-data-column > div.b1hJbf > div.dDoNo.ikb4Bb.gsrt > span.DFlfde.SwHCTb").text();
            String exchangeRateStr2 = exchangeRateStr.replace(",", ".");
            String exchangeDate    = doc.select("#knowledge-currency__updatable-data-column > div.k0Rg6d.hqAUc > span").text();
            
            LeCours = Double.parseDouble(exchangeRateStr2);
            DateCours = exchangeDate;
        }else {
            connection();
            if(conn != null) {
                try {
                    String req = "SELECT Lecours,Date_cours FROM conversion ORDER BY N_conversion DESC LIMIT 1";
                    PreparedStatement pstmt = conn.prepareStatement(req);
                    ResultSet rs = pstmt.executeQuery();
                    if(rs.next()){
                        LeCours = rs.getDouble("Lecours");
                        DateCours = rs.getString("Date_cours");
                    }
                } catch (SQLException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }
    }
    
    @WebMethod
    public String Covert_Monnaie(@WebParam String Type,@WebParam double Montant){
        double MontantConvertir = 0;
        networkCoursORlastCours();
        if(Type.equals("DZD - EUR")){
            MontantConvertir = Montant*LeCours;
        }else{
            if(Montant == 0){
                MontantConvertir = 0;
            }else{
                MontantConvertir = Montant/LeCours;
                }
        }
        
        String requete = "INSERT INTO conversion (Type,Date_conversion,Montant_a_converti,Lecours,Date_cours,Montant_converti)VALUES (?,?,?,?,?,?)";
        connection();
        try (Connection conn = DriverManager.getConnection(DBurl,"root","");PreparedStatement pstmt = conn.prepareStatement(requete)){
            pstmt.setString(1, Type);
            pstmt.setString(2, todayString);
            pstmt.setDouble(3, Montant);
            pstmt.setDouble(4, LeCours);
            pstmt.setString(5, DateCours);
            pstmt.setDouble(6, MontantConvertir);
            pstmt.executeUpdate();
            return "Conversion added "
                    + "\n montant:" + Montant
                   +"\n montant a convertir:" + MontantConvertir
                    + "\n type:"+ Type;
        } catch (SQLException ex) {
            return ex.getMessage();
        }
    }
    
    @WebMethod
    public Conversion lastConv(){
        connection();
        if(conn != null){
            try {
                String req = "SELECT * FROM conversion ORDER BY N_conversion DESC LIMIT 1";
                PreparedStatement pstmt = conn.prepareStatement(req);
                ResultSet rs = pstmt.executeQuery();
                if(rs.next()){
                    Conversion conversion = new Conversion(
                            rs.getString("Type"),
                            rs.getString("Date_conversion"),
                            rs.getDouble("Montant_a_converti"),
                            rs.getDouble("Lecours"),
                            rs.getString("Date_cours"),
                            rs.getDouble("Montant_converti"));
                    
                    return conversion;
                }
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
        return null;
    }
    
    @WebMethod
    public List<Conversion> historique(String Date, String Date2){
        connection();
        List<Conversion> Final = new ArrayList<>();
        if(conn != null){
            try {
                String req = "SELECT * FROM conversion WHERE STR_TO_DATE(Date_conversion, '%d-%m-%Y')  BETWEEN STR_TO_DATE(?, '%d-%m-%Y') AND STR_TO_DATE(?, '%d-%m-%Y')";
                PreparedStatement pstmt = conn.prepareStatement(req);
                pstmt.setString(1, Date);
                pstmt.setString(2, Date2);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()){
                    Conversion conversion = new Conversion(
                            rs.getString("Type"),
                            rs.getString("Date_conversion"),
                            rs.getDouble("Montant_a_converti"),
                            rs.getDouble("Lecours"),
                            rs.getString("Date_cours"),
                            rs.getDouble("Montant_converti"));
                    Final.add(conversion);
                }
                
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
        return Final;
    }
    
    @WebMethod
    public List<ReturnedCours> cours (String Date){
        List<ReturnedCours> coursss = new ArrayList<>();
        connection();
        if(conn != null){
            try {
                String req = "SELECT Lecours,Date_cours FROM conversion WHERE Date_cours = ?";
                PreparedStatement pstmt = conn.prepareStatement(req);
                pstmt.setString(1, Date);
                ResultSet rs = pstmt.executeQuery();
                
                
                while (rs.next()){
                    ReturnedCours returnedCours = new ReturnedCours(
                            rs.getString("Date_cours"),
                            rs.getDouble("Lecours"));
                    coursss.add(returnedCours);
                }
                
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        
        }
        return coursss;
    }
    
    public static class Conversion {
        public String Type;
        public String Date;
        public Double Montant;
        public Double Cours;
        public String Date_Cours;
        public Double Montant_convert;
        
        public Conversion(String Type, String Date, Double Montant, Double Cours, String Date_Cours, Double Montant_convert){
            this.Type = Type;
            this.Date = Date;
            this.Montant = Montant;
            this.Cours = Cours;
            this.Date_Cours = Date_Cours;
            this.Montant_convert = Montant_convert;
        }
    }
    
    public static class ReturnedCours {
        public String Date;
        public Double Cours;
        
        public ReturnedCours(String Date, Double Cours){
            this.Date = Date;
            this.Cours = Cours;
        }
    }
}
