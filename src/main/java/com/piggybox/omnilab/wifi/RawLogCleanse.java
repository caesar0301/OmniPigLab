package com.piggybox.omnilab.wifi;

import com.piggybox.utils.SimpleEvalFunc;

import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleanse and format Aruba Wifi logs by filtering out unwanted log numbers.
 *
 * Input:
 * Raw or original Aruba syslog files.
 *
 * Output:
 * 6c71d96d8c4d	2013-10-11 23:50:53	AuthRequest	XXY-3F-09
 * 6c71d96d8c4d	2013-10-11 23:50:53	AssocRequest	XXY-3F-09
 *
 * Message encoding:
 * - AuthRequest    0
 * - Deauth         1
 * - AssocRequest   2
 * - Disassoc       3
 * - UserAuth       4
 * - IPAllocation   5
 * - IPRecycle      6
 *
 * @Author chenxm
 */
public class RawLogCleanse extends SimpleEvalFunc<String>{

    // Message CODE
    final int[] CODE_AUTHREQ = {501091, 501092, 501109};
    final int[] CODE_AUTHRES = {501093, 501094, 501110};    // unused
    final int[] CODE_DEAUTH = {501105, 501080, 501098, 501099, 501106, 501107, 501108, 501111};
    final int[] CODE_ASSOCREQ = {501095, 501096, 501097};
    final int[] CODE_ASSOCRES = {501100, 501101, 501112};   // unused
    final int[] CODE_DISASSOCFROM = {501102, 501104, 501113};
    final int[] CODE_USERAUTH = {522008, 522042, 522038};   // Successful and failed
    final int[] CODE_USRSTATUS = {522005, 522006, 522026};  // User Entry added, deleted, and user miss
    final int[] CODE_USERROAM = {500010};                   // unused

    final String regPrefix = "(?<time>\\w+\\s+\\d+\\s+(\\d{1,2}:){2}\\d{1,2}\\s+\\d{4})"; // date time and year
    final String regUserMac = "(?<usermac>([0-9a-f]{2}:){5}[0-9a-f]{2})";
    final String regApInfo = "(?<apip>(\\d{1,3}\\.){3}\\d{1,3})-(?<apmac>([0-9a-f]{2}:){5}[0-9a-f]{2})-(?<apname>[\\w-]+)";

    final Pattern REG_AUTHREQ = Pattern.compile(String.format(
            "%s(.*)Auth\\s+request:\\s+%s:?\\s+(.*)AP\\s+%s",
            regPrefix, regUserMac, regApInfo), Pattern.CASE_INSENSITIVE);
    final Pattern REG_AUTHRES = Pattern.compile(String.format(
            "%s(.*)Auth\\s+(success|failure):\\s+%s:?\\s+AP\\s+%s",
            regPrefix, regUserMac, regApInfo), Pattern.CASE_INSENSITIVE);
    final Pattern REG_DEAUTH = Pattern.compile(String.format(
            "%s(.*)Deauth(.*):\\s+%s:?\\s+(.*)AP\\s+%s",
            regPrefix, regUserMac, regApInfo), Pattern.CASE_INSENSITIVE);
    final Pattern REG_ASSOCREQ = Pattern.compile(String.format(
            "%s(.*)Assoc(.*):\\s+%s(.*):?\\s+(.*)AP %s",
            regPrefix, regUserMac, regApInfo), Pattern.CASE_INSENSITIVE);
    final Pattern REG_DISASSOCFROM = Pattern.compile(String.format(
            "%s(.*)Disassoc(.*):\\s+%s:?\\s+AP\\s+%s",
            regPrefix, regUserMac, regApInfo), Pattern.CASE_INSENSITIVE);
    final Pattern REG_USERAUTH = Pattern.compile(String.format(
            "%s(.*)\\s+username=(?<username>[^\\s]+)\\s+MAC=%s\\s+IP=(?<userip>(\\d{1,3}\\.){3}\\d{1,3})(.+)(AP=(?<apname>[^\\s]+))?",
            regPrefix, regUserMac), Pattern.CASE_INSENSITIVE);

    /**NOTE: before Oct. 14, 2013, WiFi networks in SJTU deploy the IP address of 111.186.0.0/18.
     * But after that (or confirmedly Oct. 17, 2013), local addresses are utlized to save the IP
     * resources. New IP addresses deployed in WiFi networks are:
     * Local1: 1001 111.186.16.1/20, New: 10.185.0.0/16
     * Local2: 1001 111.186.33.1/21, New: 10.186.0.0/16
     * Local3: 1001 111.186.40.1/21, New: 10.188.0.0/16
     * Local4: 1001 111.186.48.1/20, New: 10.187.0.0/16
     * Local5: 1001 111.186.0.1/20, New: 10.184.0.0/16
     */
    final Pattern REG_USRSTATUS = Pattern.compile(
            String.format("%s(.*)MAC=%s\\s+IP=(?<userip>(111\\.\\d+|10\\.18[4-8])(\\.\\d+){2})",
            regPrefix, regUserMac), Pattern.CASE_INSENSITIVE);


    public String call(String rawLine) {

        // filter out valid messages
        String[] chops = new String[0];
        String cleanLog = null;

        try {
            chops = rawLine.split("<", 3);
        } catch (Exception e) {
            System.err.println(e.toString());
            return cleanLog;
        }

        if (chops.length < 3 || chops[2].length() == 0 || chops[2].charAt(0) != '5')
            return cleanLog;

        int messageCode = Integer.valueOf(chops[2].split(">", 2)[0]);

        if (hasCodes(messageCode, CODE_AUTHREQ)) { // Auth request
            Matcher matcher = REG_AUTHREQ.matcher(rawLine);
            if (matcher.find()) {
                String usermac = matcher.group("usermac").replaceAll(":", "");
                String time = formattrans(matcher.group("time"));
                cleanLog = String.format("%s\t%s\t%s\t%s\n", usermac, time, "0", matcher.group("apname"));
            }
        } else if (hasCodes(messageCode, CODE_DEAUTH)) { // Deauth from and to
            Matcher matcher = REG_DEAUTH.matcher(rawLine);
            if (matcher.find()) {
                String usermac = matcher.group("usermac").replaceAll(":", "");
                String time = formattrans(matcher.group("time"));
                cleanLog = String.format("%s\t%s\t%s\t%s\n", usermac, time, "1", matcher.group("apname"));
            }
        } else if (hasCodes(messageCode, CODE_ASSOCREQ)) { // Association request
            Matcher matcher = REG_ASSOCREQ.matcher(rawLine);
            if (matcher.find()) {
                String usermac = matcher.group("usermac").replaceAll(":", "");
                String time = formattrans(matcher.group("time"));
                cleanLog = String.format("%s\t%s\t%s\t%s\n", usermac, time, "2", matcher.group("apname"));
            }
        } else if (hasCodes(messageCode, CODE_DISASSOCFROM)) { // Disassociation
            Matcher matcher = REG_DISASSOCFROM.matcher(rawLine);
            if (matcher.find()) {
                String usermac = matcher.group("usermac").replaceAll(":", "");
                String time = formattrans(matcher.group("time"));
                cleanLog = String.format("%s\t%s\t%s\t%s\n", usermac, time, "3", matcher.group("apname"));
            }
        } else if (hasCodes(messageCode, CODE_USERAUTH)) {  //username information, User authentication
            Matcher matcher = REG_USERAUTH.matcher(rawLine);
            if (matcher.find()) {
                String usermac = matcher.group("usermac").replaceAll(":", "");
                String time = formattrans(matcher.group("time"));
                //apname is null if it is not there
                cleanLog = String.format("%s\t%s\t%s\t%s\t%s\t%s\n", usermac, time, "4", matcher.group("apname"),
                        matcher.group("username"), matcher.group("userip"));
            }
        } else if (hasCodes(messageCode, CODE_USRSTATUS)) { // User entry update status
            Matcher matcher = REG_USRSTATUS.matcher(rawLine);
            if (matcher.find()) {
                String usermac = matcher.group("usermac").replaceAll(":", "");
                String time = formattrans(matcher.group("time"));
                String iPInfo = "5";    // IP bond
                if (messageCode == 522005) {
                    iPInfo = "6";
                }
                /* From the output, we see multiple IPAllocation message between
                 * the first allocation of specific IP and its recycling action.
                 */
                cleanLog = String.format("%s\t%s\t%s\t%s\n", usermac, time, iPInfo, matcher.group("userip"));
            }
        }

        return cleanLog;
    }

    /**
     * This function is used to change the date format from "May 4" to "2013-05-04"
     */
    private String formattrans(String dateString){

        //Prepare for the month name for date changing
        TreeMap<String, String> month_tmap = new TreeMap<String, String>();
        month_tmap.put("Jan", "01");
        month_tmap.put("Feb", "02");
        month_tmap.put("Mar", "03");
        month_tmap.put("Apr", "04");
        month_tmap.put("May", "05");
        month_tmap.put("Jun", "06");
        month_tmap.put("Jul", "07");
        month_tmap.put("Aug", "08");
        month_tmap.put("Sep", "09");
        month_tmap.put("Oct", "10");
        month_tmap.put("Nov", "11");
        month_tmap.put("Dec", "12");

        //change the date from "May 4" to "2013-05-04"
        String date_reg = "(?<month>\\w+)\\s+(?<day>\\d+)\\s+(?<time>(\\d{1,2}:){2}\\d{1,2})\\s+(?<year>\\d{4})";
        Pattern date_pattern = Pattern.compile(date_reg);
        Matcher date_matcher = date_pattern.matcher(dateString);
        if(! date_matcher.find()) return null;

        String year_string=date_matcher.group("year");

        //change the month format
        String month_string = date_matcher.group("month");
        if(month_tmap.containsKey(month_string)){
            month_string = month_tmap.get(month_string);
        }else{
            System.out.println("Can not find the month!!!");
        }

        //change the day format
        String day_string = date_matcher.group("day");

        int day_int = Integer.parseInt(day_string);
        if(day_int < 10){
            day_string = "0" + Integer.toString(day_int);
        }else{
            day_string = Integer.toString(day_int);
        }

        String time_string = date_matcher.group("time");

        return String.format("%s-%s-%s %s", year_string, month_string, day_string, time_string);
    }

    /**
     * Helper to check if specific message code is contained.
     *
     * @param messageCode
     * @param codes
     * @return
     */
    private boolean hasCodes(int messageCode, int[] codes) {
        boolean flag = false;
        for (int i : codes) {
            if (messageCode == i) {
                flag = true;
                break;
            }
        }
        return flag;
    }
}
