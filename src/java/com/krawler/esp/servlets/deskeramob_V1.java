/*
 * Copyright (C) 2012  Krawler Information Systems Pvt Ltd
 * All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
package com.krawler.esp.servlets;

import java.io.IOException;
import java.text.ParseException;
import java.util.UUID;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.krawler.common.service.ServiceException;
import com.krawler.common.session.SessionExpiredException;
import com.krawler.common.timezone.Timezone;
import com.krawler.common.util.KWLErrorMsgs;
import com.krawler.common.util.KrawlerLog;
import com.krawler.common.util.SchedulingUtilities;
import com.krawler.common.util.StringUtil;
import com.krawler.database.DbPool;
import com.krawler.database.DbPool.Connection;
import com.krawler.esp.handlers.AuthHandler;
import com.krawler.esp.handlers.DashboardHandler;
import com.krawler.esp.handlers.SendMailHandler;
import com.krawler.esp.handlers.SignupHandler;
import com.krawler.esp.handlers.projdb;
import com.krawler.esp.handlers.projectReport;
import com.krawler.svnwebclient.configuration.ConfigurationException;
import com.krawler.svnwebclient.web.resource.Links;
import javax.mail.MessagingException;
import com.krawler.common.util.URLUtil;
import com.krawler.database.DbUtil;
import com.krawler.esp.handlers.*;
import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import java.sql.*;
import org.apache.commons.lang.StringUtils;
import com.krawler.esp.handlers.genericFileUpload;
import com.krawler.esp.handlers.StorageHandler;
import com.krawler.utils.json.KWLJsonConverter;
import com.krawler.utils.json.base.JSONArray;
import com.krawler.utils.json.base.JSONException;
import com.krawler.utils.json.base.JSONObject;
import java.text.SimpleDateFormat;

public class deskeramob_V1 extends HttpServlet {

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SessionExpiredException {
        response.setContentType("text/html;charset=UTF-8");
        ResultSet rs = null;
        ResultSet rsForSubQ = null;
        PreparedStatement pstmt = null;
        String result = "";
        boolean android = false;
        Connection conn = null;
        try {
            conn = DbPool.getConnection();
            int action = Integer.parseInt(request.getParameter("action"));
            int mode = Integer.parseInt(request.getParameter("mode"));
            if(!StringUtil.isNullOrEmpty(request.getParameter("android"))){
                android =Boolean.parseBoolean(request.getParameter("android"));
            }
            switch (action) {
                case 0: // generate application id
                    String u = request.getParameter("u");
                    String p = request.getParameter("p");
                    String d = request.getParameter("d");
                    result = authUser(conn, u, p, d);
                    break;

                case 1: // dashboard request
                    int limit = 15,
                    offset = 0;
                    String userid = request.getParameter("userid").toString();
                    String projectlist = DashboardHandler.getProjectList(conn, userid, 1000, 0, "");
                    JSONArray projList = null;
                    try {
                        JSONObject projListObj = new JSONObject(projectlist);
                        projList = projListObj.getJSONArray("data");
                    } catch (JSONException ex) {
                        result = "{\"data\":[{\"success\":false,\"data\":" + ex.getMessage() + "}]}";
                    }
                    switch (mode) {
                        case 1: // due tasks
                            try {
                                boolean projMod = false;
                                for(int c = 0; c < projList.length(); c++){
                                    JSONObject te = projList.getJSONObject(c);
                                    if(DashboardHandler.isModerator(conn, userid, te.getString("id"))){
                                        projMod = true;
                                        break;
                                    }
                                }
                                PreparedStatement pstmt1 = null;
                                JSONObject jobj = new JSONObject();
                                String query = "Select count(post_id) AS count from mailmessages inner join users " +
                                        "on users.userid = mailmessages.poster_id where folder = ? and to_id = ? and readflag = false ORDER BY post_time";
                                pstmt1 = conn.prepareStatement(query);
                                pstmt1.setString(1, "0");
                                pstmt1.setString(2, userid);
                                ResultSet rs1 = pstmt1.executeQuery();
                                int count = 0;
                                if (rs1.next()) {
                                    count = rs1.getInt("count");
                                }
                                if (projList == null) {
                                    result = "{\"success\":[{\"result\": true}],\"msgcount\":[{\"count\":\"" + Integer.toString(count) + "\"}],\"data\":[]}";
                                } else {
                                    for (int i = 0; i < projList.length(); i++) {
                                        JSONObject temp = projList.getJSONObject(i);
                                        String projid = temp.getString("id");
                                        String qry = "";
                                        String projName = "";
                                        boolean moderator = DashboardHandler.isModerator(conn, userid, projid);
                                        if (!moderator) {
                                            qry = "(SELECT taskid FROM proj_task WHERE projectid=? AND taskid NOT IN " +
                                                    "(SELECT taskid FROM proj_taskresourcemapping)) " +
                                                    "UNION " +
                                                    "(SELECT taskid FROM proj_taskresourcemapping WHERE resourceid = ? " +
                                                    "AND taskid IN (SELECT taskid FROM proj_task WHERE projectid = ?))";
                                            pstmt1 = conn.prepareStatement(qry);
                                            pstmt1.setString(1, projid);
                                            pstmt1.setString(2, userid);
                                            pstmt1.setString(3, projid);
                                        } else {
                                            qry = "SELECT taskid FROM proj_task WHERE projectid = ?";
                                            pstmt1 = conn.prepareStatement(qry);
                                            pstmt1.setString(1, projid);
                                        }
                                        rs1 = pstmt1.executeQuery();
                                        String tids = "";
                                        while (rs1.next()) {
                                            tids += "'" + rs1.getString("taskid") + "',";
                                        }
                                        if (tids.length() > 0) {
                                            tids = tids.substring(0, (tids.length() - 1));
                                            pstmt1 = conn.prepareStatement("SELECT projectname FROM project WHERE projectid = ?");
                                            pstmt1.setString(1, projid);
                                            rs1 = pstmt1.executeQuery();
                                            if (rs1.next()) {
                                                projName = rs1.getString("projectname");
                                            }
                                            qry = "SELECT priority,taskname,percentcomplete,DATE_FORMAT(startdate,'%D %b %y') AS startdate,DATE_FORMAT(enddate,'%D %b %y') AS enddate, taskid " +
                                                    "FROM proj_task " +
                                                    "WHERE percentcomplete < 100 AND taskid IN (" + tids + ") AND (date(enddate)>=date(now())) AND (date(startdate) <= date(now()))";
                                            //                                            pstmt.setInt(1, limit);
                                            pstmt1 = conn.prepareStatement(qry);
                                            rs1 = pstmt1.executeQuery();
                                            while (rs1.next()) {
                                                JSONObject j = new JSONObject();
                                                j.put("projectname", projName);
                                                j.put("taskname", rs1.getString("taskname"));
                                                j.put("taskid", rs1.getString("taskid"));
                                                j.put("complete", rs1.getString("percentcomplete"));
                                                j.put("startdate", rs1.getString("startdate"));
                                                j.put("enddate", rs1.getString("enddate"));
                                                int ptr = rs1.getInt("priority");
                                                String pStr = "medium";
                                                if (ptr == 0) {
                                                    pStr = "high";
                                                } else if (ptr == 2) {
                                                    pStr = "low";
                                                }
                                                j.put("priority", pStr);
                                                if (moderator) {
                                                    String res = DashboardHandler.getTaskResources(conn, rs1.getString("taskid"), projid, userid);
                                                    if (!StringUtil.equal(res, "{}")) {
                                                        JSONObject jobj1 = new JSONObject(res);
                                                        JSONArray jarr = jobj1.getJSONArray("data");
                                                        String resr = "";
                                                        for (int cnt = 0; cnt < jarr.length(); cnt++) {
                                                            resr += jarr.getJSONObject(cnt).getString("resourcename") + ", ";
                                                        }
                                                        resr = resr.substring(0, (resr.length() - 2));
                                                        if (!StringUtil.isNullOrEmpty(resr)) {
                                                            j.put("assignedto", resr);
                                                        }
                                                    }
                                                }
                                                jobj.append("data", j);
                                            }
                                        }
                                    }
                                }
                                if (jobj.has("data")) {
                                    JSONObject fin = new JSONObject();
                                    fin.put("count", String.valueOf(count));
                                    jobj.append("msgcount", fin);
                                    jobj.put("moderator", projMod);
                                    result = jobj.toString();
                                } else {
                                    result = "{\"success\":[{\"result\": true}],\"msgcount\":[{\"count\":\"" + Integer.toString(count) + "\"}],\"data\":[],\"moderator\":" + Boolean.toString(projMod) + "}";
                                }
                            } catch (JSONException ex) {
                            }
                            break;
                        case 0: // overdue tasks
                            try {
                                JSONObject jobj = new JSONObject();
                                PreparedStatement pstmt1 = null;
                                String query = "Select count(post_id) AS count from mailmessages inner join users " +
                                        "on users.userid = mailmessages.poster_id where folder = ? and to_id = ? and readflag = false ORDER BY post_time";
                                pstmt1 = conn.prepareStatement(query);
                                pstmt1.setString(1, "0");
                                pstmt1.setString(2, userid);
                                ResultSet rs1 = pstmt1.executeQuery();
                                int count = 0;
                                if (rs1.next()) {
                                    count = rs1.getInt("count");
                                }
                                if (projList == null) {
                                    result = "{\"success\":[{\"result\": true}],\"msgcount\":[{\"count\":\"" + Integer.toString(count) + "\"}],\"data\":[]}";
                                } else {
                                    for (int i = 0; i < projList.length(); i++) {
                                        JSONObject temp = projList.getJSONObject(i);
                                        String projid = temp.getString("id");
                                        String qry = "";
                                        String projName = "";
                                        boolean moderator = DashboardHandler.isModerator(conn, userid, projid);
                                        if (!moderator) {
                                            qry = "(SELECT taskid FROM proj_task WHERE projectid=? AND taskid NOT IN " +
                                                    "(SELECT taskid FROM proj_taskresourcemapping)) " +
                                                    "UNION " +
                                                    "(SELECT taskid FROM proj_taskresourcemapping WHERE resourceid = ?)";
                                            pstmt1 = conn.prepareStatement(qry);
                                            pstmt1.setString(1, projid);
                                            pstmt1.setString(2, userid);
                                        } else {
                                            qry = "SELECT taskid FROM proj_task WHERE projectid = ?";
                                            pstmt1 = conn.prepareStatement(qry);
                                            pstmt1.setString(1, projid);
                                        }
                                        rs1 = pstmt1.executeQuery();
                                        String tids = "";
                                        while (rs1.next()) {
                                            tids += "'" + rs1.getString("taskid") + "',";
                                        }
                                        if (tids.length() > 0) {
                                            tids = tids.substring(0, (tids.length() - 1));
                                            pstmt1 = conn.prepareStatement("SELECT projectname FROM project WHERE projectid = ?");
                                            pstmt1.setString(1, projid);
                                            rs1 = pstmt1.executeQuery();
                                            if (rs1.next()) {
                                                projName = rs1.getString("projectname");
                                            }
                                            qry = "SELECT priority,taskname,percentcomplete, datediff(CURRENT_DATE,date(enddate)) as overdueby,DATE_FORMAT(enddate,'%D %b %y') AS enddate, taskid " +
                                                    "FROM proj_task " +
                                                    "WHERE percentcomplete < 100 AND taskid IN (" + tids + ") AND date(proj_task.enddate) < date(now()) LIMIT ? OFFSET ?";
                                            pstmt1 = conn.prepareStatement(qry);
                                            pstmt1.setInt(1, limit);
                                            pstmt1.setInt(2, offset);
                                            rs1 = pstmt1.executeQuery();
                                            while (rs1.next()) {
                                                JSONObject j = new JSONObject();
                                                j.put("projectname", projName);
                                                j.put("taskid", rs1.getString("taskid"));
                                                j.put("taskname", rs1.getString("taskname"));
                                                j.put("complete", rs1.getString("percentcomplete"));
                                                j.put("overdueby", rs1.getString("overdueby"));
                                                j.put("enddate", rs1.getString("enddate"));
                                                int ptr = rs1.getInt("priority");
                                                String pStr = "medium";
                                                if (ptr == 0) {
                                                    pStr = "high";
                                                } else if (ptr == 2) {
                                                    pStr = "low";
                                                }
                                                j.put("priority", pStr);
                                                if (moderator) {
                                                    String res = DashboardHandler.getTaskResources(conn, rs1.getString("taskid"), projid, userid);
                                                    if (!StringUtil.equal(res, "{}")) {
                                                        JSONObject jobj1 = new JSONObject(res);
                                                        JSONArray jarr = jobj1.getJSONArray("data");
                                                        String resr = "";
                                                        for (int cnt = 0; cnt < jarr.length(); cnt++) {
                                                            resr += jarr.getJSONObject(cnt).getString("resourcename") + ", ";
                                                        }
                                                        resr = resr.substring(0, (resr.length() - 2));
                                                        if (!StringUtil.isNullOrEmpty(resr)) {
                                                            j.put("assignedto", resr);
                                                        }
                                                    }
                                                }
                                                jobj.append("data", j);
                                            }
                                        }
                                    }
                                }
                                if (jobj.has("data")) {
                                    JSONObject fin = new JSONObject();
                                    fin.put("count", count);
                                    jobj.append("msgcount", fin);
                                    result = jobj.toString();
                                } else {
                                    result = "{\"success\":[{\"result\": true}],\"msgcount\":[{\"count\":\"" + Integer.toString(count) + "\"}],\"data\":[]}";
                                }
                            } catch (JSONException ex) {
                            }
                            break;
                        case 2: // calendar events
                            PreparedStatement pstmt1 = null;
                            String query = "Select count(post_id) AS count from mailmessages inner join users " +
                                    "on users.userid = mailmessages.poster_id where folder = ? and to_id = ? and readflag = false ORDER BY post_time";
                            pstmt1 = conn.prepareStatement(query);
                            pstmt1.setString(1, "0");
                            pstmt1.setString(2, userid);
                            ResultSet rs1 = pstmt1.executeQuery();
                            int count = 0;
                            if (rs1.next()) {
                                count = rs1.getInt("count");
                            }
                            String timezone = "+00:00";
                            timezone = Timezone.getTimeZone(conn, userid);
                            String sqlquery = "SELECT calendarevents.subject,project.projectname, startts, endts, DATE_FORMAT(calendarevents.startts,'%D %b %y') AS 'startdate' ," +
                                    "DATE_FORMAT(calendarevents.startts,'%h:%i %p') AS 'starttime'," +
                                    "CASE calendarevents.priority WHEN 'm' THEN 'Medium' WHEN 'l' THEN 'Low' WHEN 'h' THEN 'High' END AS priority" +
                                " FROM calendarevents INNER JOIN calendars ON calendars.cid =calendarevents.cid INNER JOIN project ON project.projectid = calendars.userid " +
                                " WHERE project.projectid IN (SELECT project.projectid FROM project INNER JOIN projectmembers ON " +
                                " projectmembers.projectid = project.projectid WHERE userid = ?) AND calendars.timestamp> ? " +
                                " AND date(startts)>=CURRENT_DATE AND date(startts)<=(ADDDATE(CURRENT_DATE, 7)) ORDER BY startts LIMIT ? OFFSET ?";
                            pstmt = conn.prepareStatement(sqlquery);
                            pstmt.setString(1, userid);
                            pstmt.setString(2, "1970-01-01 00:00:00");
                            pstmt.setInt(3, limit);
                            pstmt.setInt(4, offset);
                            rsForSubQ = pstmt.executeQuery();
                            try {
                                JSONObject jobj = new JSONObject();
                                while (rsForSubQ.next()) {
                                    timezone = timezone.substring(0,4) + "00";
                                    String startts = rsForSubQ.getString("startts");
                                    startts = Timezone.toUserDefTimezone(conn, startts, timezone);
                                    Date startDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startts);
                                    int date = Integer.parseInt(new SimpleDateFormat("d").format(startDate));
                                    String ordinal = getDateOrdinal(date);
                                    JSONObject j = new JSONObject();
                                    j.put("subject", rsForSubQ.getString("subject"));
                                    j.put("projectname", rsForSubQ.getString("projectname"));
                                    j.put("startdate", new SimpleDateFormat("d'".concat(ordinal).concat("' MMM yy")).format(startDate));
                                    j.put("starttime", new SimpleDateFormat("hh:mm a").format(startDate));
                                    j.put("priority", rsForSubQ.getString("priority"));
                                    jobj.append("data", j);
                                }
                                if (jobj.has("data")) {
                                    JSONObject fin = new JSONObject();
                                    fin.put("count", String.valueOf(count));
                                    jobj.append("msgcount", fin);
                                    result = jobj.toString();
                                } else {
                                    result = "{\"success\":true,\"data\":[]}";
                                }
                            } catch (ParseException ex) {
                                result = "{\"success\":false,\"data\":" + ex.getMessage() + "}";
                            } catch (JSONException ex) {
                                result = "{\"success\":false,\"data\":" + ex.getMessage() + "}";
                            }
                            //result = kwljson.GetJsonForGrid(rsForSubQ);
                            break;
                        case 3: // unread personal msgs
                            query = "Select count(post_id) AS count from mailmessages inner join users " +
                                    "on users.userid = mailmessages.poster_id where folder = ? and to_id = ? and readflag = false ORDER BY post_time";
                            pstmt = conn.prepareStatement(query);
                            pstmt.setString(1, "0");
                            pstmt.setString(2, userid);
                            rsForSubQ = pstmt.executeQuery();
                            count = 0;
                            if (rsForSubQ.next()) {
                                count = rsForSubQ.getInt("count");
                            }
                            query = "Select post_id ,concat(fname,' ',lname) as post_fullname,userlogin.username as poster_id , post_text , post_subject ," +
                                    " post_time from mailmessages inner join users " +
                                    "on users.userid = mailmessages.poster_id inner join userlogin on users.userid=userlogin.userid where folder = ? and to_id = ? and readflag = false ORDER BY post_time DESC";
                            pstmt = conn.prepareStatement(query);
                            pstmt.setString(1, "0");
                            pstmt.setString(2, userid);
//				pstmt.setInt(3, limit);
//				pstmt.setInt(4, offset);
                            rsForSubQ = pstmt.executeQuery();
                            //result = kwljson.GetJsonForGrid(rsForSubQ);
                            try {
                                JSONObject jobj = new JSONObject();
                                String companyid = getCompanyID(conn, userid);
                                String subdomain = CompanyHandler.getCompanySubdomainByCompanyID(conn, companyid);
                                while (rsForSubQ.next()) {
                                    JSONObject j = new JSONObject();
                                    String postTime = Timezone.toCompanyTimezone(conn, rsForSubQ.getString("post_time"), companyid);
                                    Date startDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(postTime);
                                    int date = Integer.parseInt(new SimpleDateFormat("d").format(startDate));
                                    String ordinal = getDateOrdinal(date);
                                    j.put("post_id", rsForSubQ.getString("post_id"));
                                    j.put("post_fullname", rsForSubQ.getString("post_fullname"));
                                    j.put("poster_id", rsForSubQ.getString("poster_id"));
                                    j.put("post_text", insertSmiley(rsForSubQ.getString("post_text"), URLUtil.getPageURL(request, com.krawler.esp.web.resource.Links.loginpageFull, subdomain)));
                                    j.put("post_subject", rsForSubQ.getString("post_subject"));
                                    j.put("post_time", new SimpleDateFormat("dd'".concat(ordinal).concat("' MMM yy").concat(" hh:mm a")).format(startDate));
                                    jobj.append("data", j);
                                }
                                if (jobj.has("data")) {
                                    JSONObject temp = new JSONObject();
                                    temp.put("count", String.valueOf(count));
                                    jobj.append("msgcount", temp);
                                    result = jobj.toString();
                                } else {
                                    result = "{\"success\":true,\"data\":[]}";
                                }
                            } catch (ParseException ex) {
                                result = "{\"success\":false,\"data\":" + ex.getMessage() + "}";
                            } catch (JSONException ex) {
                                result = "{\"success\":false,\"data\":" + ex.getMessage() + "}";
                            }
                            break;
                        case 4: // list of users's projects
                            String companyid = getCompanyID(conn, userid);
//                            boolean isSuperUser = DashboardHandler.isSuperUser(conn, companyid, userid);
                            try {
                                  JSONObject jobj = getUserProjectList(conn,userid);
//                                JSONObject projectList = new JSONObject(DashboardHandler.getProjectListMember(conn, userid, 10, 0));
//                                JSONArray projArray = projectList.getJSONArray("data");
//                                int prc = projArray.length();
//                                JSONObject jobj = new JSONObject();
//                                if (prc > 0) {
//                                    for (int i = 0; i < projArray.length(); i++) {
//                                        JSONObject tempProj = projArray.getJSONObject(i);
//                                        if(tempProj.getInt("status") > 3){
//                                            JSONObject j = new JSONObject();
//                                            j.put("name", tempProj.getString("name"));
//                                            j.put("id", tempProj.getString("id"));
//                                            jobj.append("data", j);
//                                        }
//                                    }
//                                }
                                if (jobj.has("data")) {
                                    result = jobj.toString();
                                } else {
                                    result = "{\"success\":true,\"data\":[]}";
                                }
                            } catch (JSONException ex) {
                                result = "{\"success\":false,\"data\":" + ex.getMessage() + "}";
                            }
                            break;
                        case 5: // list of project resources
                            try{
                                JSONObject jobj = new JSONObject();
                                jobj = getProjectResourceList(conn,request);
//                            String responseText = projdb.getProjectResources(conn, request.getParameter("id"));
//                            try {
//                                JSONObject jobj = new JSONObject();
//                                if (responseText.compareTo("{data:{}}") != 0) {
//                                    JSONObject resJOBJ = new JSONObject(responseText);
//                                    JSONArray jArray = resJOBJ.getJSONArray("data");
//                                    int prec = jArray.length();
//                                    if (prec > 0) {
//                                        for (int i = 0; i < prec; i++) {
//                                            JSONObject j = new JSONObject();
//                                            j.put("name", jArray.getJSONObject(i).getString("resourcename"));
//                                            j.put("id", jArray.getJSONObject(i).getString("resourceid"));
//                                            jobj.append("data", j);
//                                        }
//                                    }
//                                }
                                if (jobj.has("data")) {
                                    result = jobj.toString();
                                } else {
                                    result = "{\"success\":true,\"data\":[]}";
                                }
                            } catch (JSONException ex) {
                                result = "{\"success\":false,\"data\":" + ex.getMessage() + "}";
                            }
                            break;
                        case 6:// display project list
                            if(!AdminServlet.isCreator(conn, userid) || android){
                                result = projectlist;
                            } else {
                                companyid = getCompanyID(conn, userid);
                                result = AdminServlet.getProjData(conn, request, companyid, "");
                            }
                            break;
                        case 7:// fetch assigned/unassigned members
                            result = getAssiUnAssiProjctMembers(conn, request.getParameter("projectid"));
                            break;
                        case 8:// isSuperUser
                            int projectcount = 0;
                            companyid = getCompanyID(conn, userid);
                            boolean isSuper = DashboardHandler.isSuperUser(conn, companyid, userid);
                            JSONObject temp = new JSONObject();
                            JSONObject jobj = new JSONObject();
                            temp.put("superuser", isSuper);
                            jobj.append("data", temp);
                            try{
                                JSONObject projectList = getUserProjectList(conn, userid);
                                JSONArray projectArray = projectList.getJSONArray("data");
                            for(int c = 0; c < projectArray.length(); c++){
                                    JSONObject te = projectArray.getJSONObject(c);
                                    request.setAttribute("id", te.getString("id"));
                                    JSONArray member = getProjectResourceList(conn, request).getJSONArray("data");
                                    for(int i=0;i<member.length();i++){
                                         JSONObject j = member.getJSONObject(i);
                                         jobj.append("projectmember",j);
                                    }
                                    jobj.append("data",te);
                                    projectcount++;
                            }
                            }catch(Exception e){
                                 result = e.toString();
                            }
                            jobj.put("projectcount", projectcount);
                            jobj.put("userid",userid);
                            result = jobj.toString();
                            break;
                        case 9:// manage members
                            String userids = request.getParameter("userid");
                            if (StringUtil.isNullOrEmpty(userids)) {
                                result = "{\"result\":[{\"success\":false}], \"msg\":[{\"msgText\": \"Can not remove all project members.\"}]}";
                            } else {
                                userids = userids.substring(0, (userids.length() - 1));
                                String[] uids = userids.split(",");
                                String pid = request.getParameter("projectid");
                                result = manageMembers(conn, pid, uids, userid);
                            }
                            break;
                        case 10: // company user list
                            companyid = getCompanyID(conn, userid);
                            String companyMembers = AdminServlet.getAdminUserData(conn, request, companyid, "", false);
//                            result = "{\"result\":[{\"success\":false}], \"msg\":[{\"msgText\": \"Could not complete your request.\"}]}";
                            result = "{\"result\":[{\"success\":true}],\"data\":" + companyMembers + "}";
                            break;
                    }
                    break;

                case 2: // Update Records
                    userid = request.getParameter("userid").toString();
                    switch (mode) {
                        case 1:// set read flag
                            String post_id = request.getParameter("post_id");
                            String query = "update mailmessages set readflag=true where post_id = ?";
                            pstmt = conn.prepareStatement(query);
                            pstmt.setString(1, post_id);
                            int rows = pstmt.executeUpdate();
                            if (rows > 0) {
                                conn.commit();
                                result = "{\"success\":true}";
                            } else {
                                result = "{\"success\":false}";
                            }
                            break;
                        case 2:// update percent value for record
                            String taskid = request.getParameter("taskid");
                            String pcomplete = request.getParameter("complete");
                            query = "update proj_task set percentcomplete = ? where taskid = ?";
                            pstmt = conn.prepareStatement(query);
                            pstmt.setString(1, pcomplete);
                            pstmt.setString(2, taskid);
                            rows = pstmt.executeUpdate();
                            if (rows > 0) {
                                conn.commit();
                                result = "{\"success\":true}";
                            } else {
                                result = "{\"success\":false}";
                            }
                            break;
                        case 3:// insert tasks
                            try {
                                String projId = request.getParameter("projectid");
                                pstmt = conn.prepareStatement("select max(taskindex) as maxindex from proj_task where projectid=?");
                                pstmt.setString(1, projId);
                                rs = pstmt.executeQuery();
                                int rowindex = 1;
                                if (rs.next()) {
                                    rowindex = rs.getInt(1) + 1;
                                }
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd");
                                taskid = UUID.randomUUID().toString();
                                String taskname = request.getParameter("name");
                                String stdate = request.getParameter("start");
                                String enddate = request.getParameter("end");
                                int priority = 1;
                                if (!StringUtil.isNullOrEmpty(request.getParameter("priority"))) {
                                    priority = Integer.parseInt(request.getParameter("priority"));
                                }
                                pstmt = conn.prepareStatement("INSERT INTO proj_task(taskid, taskname, duration, startdate, enddate, projectid, " +
                                        "taskindex, level, parent, actualstartdate, actualduration, percentcomplete, notes, priority, " +
                                        "isparent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                                pstmt.setString(1, taskid);
                                pstmt.setString(2, taskname);
                                java.util.Date DateVal = sdf.parse(stdate);
                                java.util.Date sDate = sdf.parse(stdate);
                                Timestamp ts = new Timestamp(DateVal.getTime());
                                pstmt.setTimestamp(4, ts);
                                DateVal = sdf.parse(enddate);
                                int duration = SchedulingUtilities.calculateWorkingDays(conn, projId, sDate, DateVal);
                                pstmt.setString(3, String.valueOf(duration));
                                ts = new Timestamp(DateVal.getTime());
                                pstmt.setTimestamp(5, ts);
                                pstmt.setString(6, projId);
                                pstmt.setString(7, String.valueOf(rowindex));
                                pstmt.setString(8, "0");
                                pstmt.setString(9, "0");
                                DateVal = sdf.parse(stdate);
                                ts = new Timestamp(DateVal.getTime());
                                pstmt.setTimestamp(10, ts);
                                pstmt.setString(11, String.valueOf(duration));
                                //if(request.getParameter("completed").equals(null))
                                     pstmt.setString(12, "0");
                               // else
                                 //   pstmt.setString(12, request.getParameter("completed"));
                                pstmt.setString(13, "");
                                pstmt.setInt(14, priority);
                                pstmt.setBoolean(15, false);
                                boolean flag = pstmt.execute();
                                if (!request.getParameter("assignto").equals("")) {
                                    String[] resArray = request.getParameter("assignto").split(",");
                                    for (int i = 0; i < resArray.length; i++) {
                                        int dur = 0;
                                        String rid = resArray[i];
                                        pstmt = conn.prepareStatement("insert into proj_taskresourcemapping (taskid,resourceid,resduration) values(?,?,?)");
                                        pstmt.setString(1, taskid);
                                        pstmt.setString(2, rid);
                                        pstmt.setInt(3, dur);
                                        pstmt.execute();
                                    }
                                }
                                conn.commit();
                                result = "{\"success\":[{\"result\":true}]}";
                            } catch (ParseException ex) {
                                Logger.getLogger(deskeramob.class.getName()).log(Level.SEVERE, null, ex);
                                result = "{\"success\":[{\"result\":false}],\"data\":" + ex.getMessage() + "}";
                            }
                            break;
                        case 4: // create user
//                            String companyid = getCompanyID(conn, userid);
//                            result = createUser(conn, request, companyid, userid);
//                            if (!StringUtil.isNullOrEmpty(result) && result.contains("failure")) {
//                                result = "{\"success\":[{\"result\":false,\"error\":\"Could not complete your request. Please try again later.\"}]}";
//                            } else {
//                                conn.commit();
//                                result = "{\"success\":[{\"result\":true,\"error\":\"User created successfully.\"}]}";
//                            }
                            break;
                        case 5: // import contacts
                            result = importContacts(conn, request);
                            break;
                        case 6: // export contacts
                            result = exportContacts(conn, request);
                            if (!StringUtil.isNullOrEmpty(result)) {
                                result = "{\"import\":[{\"result\":true}]," + result.substring(1, (result.length() - 1)) + "}";
                            } else {
                                result = "{\"import\":[{\"result\":true,\"error\":\"There seem to be some problem with server. Could not import contacts.\"}]}";
                            }
                            break;
                    }
                    break;

                case 3: // company updates
                    switch (mode) {
                        case 1:// create new company
//                            result = createNewCompany(conn, request, response);
//                            JSONObject j = new JSONObject(result);
//                            if (j.getJSONArray("data").getJSONObject(0).has("success") && j.getJSONArray("data").getJSONObject(0).getString("success").equals("true")) {
//                                result = authUser(conn, request.getParameter("u"), request.getParameter("p"), request.getParameter("cdomain"));
//                            }
                            break;
                        case 2: //create project
                            userid = request.getParameter("userid").toString();
                            String subdomain = CompanyHandler.getCompanySubdomainByUser(conn, userid);
                            String companyid = getCompanyID(conn, userid);
                            result = createProject(conn, request, companyid, subdomain, userid);
                            if (StringUtil.equal("success", result)) {
                                result = "{\"success\":[{\"result\":true}]}";
                            } else {
                                result = "{\"success\":[{\"result\":false,\"error\":\"" + result + "\"}]}";
                            }
                            break;

                        case 3: //delete project
                            userid = request.getParameter("userid").toString();
                            companyid = getCompanyID(conn, userid);
                            result = AdminServlet.deleteProject(conn, request, companyid, userid, "iPhone");
                            if (StringUtil.equal("failure", result)) {
                                result = "{\"success\":[{\"result\":false}]}";
                                DbPool.quietRollback(conn);
                            } else {
                                result = "{\"success\":[{\"result\":true}]}";
                                conn.commit();
                            }
                            break;
                    }
                    break;
                    
                case 4: //Project Reports
                    switch (mode) {
                        case 0://Project chart(type="milestones")
                            if (!StringUtil.isNullOrEmpty(request.getParameter("projid")) && !StringUtil.isNullOrEmpty(request.getParameter("uid"))) {
                                result = DashboardHandler.getChartURL(conn, request.getParameter("projid"), request.getParameter("uid"), "progress");
                            }
                            break;
                        case 1: //milestone(rtype="milestone"), overdue(rtype="overdue"), task in progress(rtype="taskinprogress", percent=-99), un-started task(rtype="taskinprogress", percent=0)
                            if (!StringUtil.isNullOrEmpty(request.getParameter("projid")) && !StringUtil.isNullOrEmpty(request.getParameter("userid"))) {
                                result = projectReport.getProjectReportJson(request, false, false);
                                if(result.compareToIgnoreCase("{data:{}}")==0){
                                    result = "{\"data\":[]}";
                                }
                            }
                            break;
                    }
                    break;
            }
        } catch (JSONException ex) {
            Logger.getLogger(deskeramob.class.getName()).log(Level.SEVERE, null, ex);
            result = "{\"success\":[{\"result\":\"1\"}],\"data\":" + ex.getMessage() + "}";
        } catch (ServiceException ex) {
            result = "{\"success\":[{\"result\":\"0\"}],\"data\":" + ex.getMessage() + "}";
        } catch (SQLException ex) {
            result = "{\"success\":[{\"result\":\"0\"}],\"data\":" + ex.getMessage() + "}";
        } finally {
            DbPool.quietClose(conn);
            response.getWriter().println(result);
        }
        response.getWriter().close();
    }

    public String exportContacts(Connection conn, HttpServletRequest request) {
        String res = "";
        String query = "SELECT concat(users.fname, ' ', users.lname) AS name,users.emailid, users.contactno AS phone, users.address,\"\" AS recordid" +
                " FROM userrelations INNER JOIN users ON (users.userid = userrelations.userid2 or users.userid = userrelations.userid1)" +
                " WHERE users.userid != ? AND (userid1 = ? or userid2 = ?)" +
                " UNION SELECT addressbook.name AS name,addressbook.emailid,addressbook.phone,addressbook.address,recid AS recordid" +
                " FROM addressbook WHERE userid = ?";
        try {
            String userid = request.getParameter("userid").toString();
            PreparedStatement p = conn.prepareStatement(query);
            p.setString(1, userid);
            p.setString(2, userid);
            p.setString(3, userid);
            p.setString(4, userid);
            ResultSet r = p.executeQuery();
            KWLJsonConverter kj = new KWLJsonConverter();
            res = kj.GetJsonForGrid(r);
        } catch (ServiceException ex) {
            Logger.getLogger(deskeramob.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException e) {
            Logger.getLogger(deskeramob.class.getName()).log(Level.SEVERE, null, e);
        }
        return res;
    }

    public String importContacts(Connection conn, HttpServletRequest request) {
        String ret = "";
        try {
            String userid = request.getParameter("userid").toString();
            boolean flg = true;
            String[] contRec = request.getParameter("contactData").split("\n");
            for (int i = 0; i < contRec.length; i++) {
                String[] temp = contRec[i].split(",");
                String name = temp[0] + " " + temp[1];
                if (!StringUtil.isNullOrEmpty(temp[2]) && !StringUtil.isNullOrEmpty(name)) {
                    String cid = AddressBookServlet.chkRecId(conn, userid, temp[3]);
                    if (StringUtil.isNullOrEmpty(cid)) {
                        cid = AddressBookServlet.chkDuplicateRec(conn, userid, name, "", temp[2], "", temp[3]);
                    }
                    if (!StringUtil.isNullOrEmpty(cid)) {
                        AddressBookServlet.updateContact(conn, userid, name, "", temp[2], "", temp[3], cid);
                    } else {
                        AddressBookServlet.insertContact(conn, userid, name, "", temp[2], "", temp[3]);
                    }
                }
            }
            if (flg) {
                ret = "{\"import\":[{\"result\":true}]}";
                conn.commit();
            } else {
                ret = "{\"import\":[{\"result\":false}]}";
                DbPool.quietRollback(conn);
            }
        } catch (ServiceException ex) {
            Logger.getLogger(deskeramob.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;
    }

    public static String manageMembers(Connection conn, String pid, String[] uids, String lid) {
        String res = "";
        String userids = "";
        for (int i = 0; i < uids.length; i++) {
            userids += "\'" + uids[i] + "\',";
        }
        userids = userids.substring(0, (userids.length() - 1));
        try {
            PreparedStatement pstmt = conn.prepareStatement("SELECT subdomain FROM company WHERE companyid = (SELECT companyid FROM project WHERE projectid = ?)");
            pstmt.setString(1, pid);
            ResultSet rs = pstmt.executeQuery();
            int flg = 0;
            String subdomain = "";
            if (rs.next()) {
                subdomain = rs.getString("subdomain");
            }
            pstmt = conn.prepareStatement("SELECT userid FROM projectmembers WHERE projectid = ? AND userid NOT IN (" + userids + ")");
            pstmt.setString(1, pid);
            rs = pstmt.executeQuery();
            while (rs.next()) { //remove members from project
                String temp = rs.getString("userid");
                if (DashboardHandler.isModerator(conn, pid, temp) && Forum.isLastModerator(conn, pid, temp)) {
                    flg = 1;
                    break;
                } else {
                    String resval = Forum.setStatusProject(conn, temp, pid, 0, 0, lid, subdomain);
                    if (resval.contains("error")) {
                        flg = 2;
                        break;
                    }
                }
            }
            if (flg == 0) {
                for (int i = 0; i < uids.length; i++) {
                    int oS = Forum.getStatusProject(conn, uids[i], pid);
                    if(oS == 0)
                        oS = 3;
                    if(oS != 4){
                        String resval = Forum.setStatusProject(conn, uids[i], pid, oS, 0, lid, subdomain);
                        if (resval.contains("error")) {
                            flg = 2;
                            break;
                        }
                    }
                }
            }
            if (flg != 0) {
                if (flg == 1) {
                    res = "[{\"result\":\"false\"}], \"msg\":[{\"msgText\": \"Can not remove all the moderators from the project.\"}]";
                } else if (flg == 2) {
                    res = "[{\"result\":\"false\"}], \"msg\":[{\"msgText\": \"Could not complete your request at the moment.\"}]";
                }
                DbPool.quietRollback(conn);
            } else {
                res = "{\"result\":[{\"success\":true}]}";
                conn.commit();
            }
        } catch (SQLException ex) {
            Logger.getLogger(deskeramob.class.getName()).log(Level.SEVERE, null, ex);
            res = "[{\"result\":\"false\"}], \"msg\":[{\"msgText\": \"Could not complete your request at the moment.\"}]";
        } catch (ServiceException e) {
            res = "[{\"result\":\"false\"}], \"msg\":[{\"msgText\": \"Could not complete your request at the moment.\"}]";
        }
        return res;
    }

    public static String insertSmiley(String msg, String serverAdd) {
        String result = "";
        String[] smileyStore = {":)", ":(", ";)", ":D", ";;)", "&gt;:D&lt;", ":-/", ":x", ":&gt;&gt;", ":P", ":-*", "=((", ":-O", "X(", ":&gt;", "B-)", ":-S", "#:-S", "&gt;:)", ":((", ":))", ":|", "/:)", "=))", "O:-)", ":-B", "=;", ":-c"};
        java.util.List smileyListArry = java.util.Arrays.asList(smileyStore);
        String smileyRegx = "(:\\(\\()|(:\\)\\))|(:\\))|(:x)|(:\\()|(:P)|(:D)|(;\\))|(;;\\))|(&gt;:D&lt;)|(:-\\/)|(:&gt;&gt;)|(:-\\*)|(=\\(\\()|(:-O)|(X\\()|(:&gt;)|(B-\\))|(:-S)|(#:-S)|(&gt;:\\))|(:\\|)|(\\/:\\))|(=\\)\\))|(O:-\\))|(:-B)|(=;)|(:-c)";
        java.util.regex.Pattern myPattern = java.util.regex.Pattern.compile(smileyRegx);
        java.util.regex.Matcher myMatcher = myPattern.matcher(msg);
        int no = 0;
        String replaceString = "";
        try {
            while (myMatcher.find()) {
                no = smileyListArry.indexOf(myMatcher.group()) + 1;
                replaceString = "<img src=\"" + serverAdd + "/images/smiley" + no + ".gif\" style=display:inline;vertical-align:text-top;></img>";
                msg = StringUtils.replaceOnce(msg, myMatcher.group(), replaceString);
            }
        } catch (Exception e) {
            System.out.print(e);
        }
        return msg;
    //return result;
    }

    public static void main(String arr[]) {
        String msg = "Hi :) and ;;)  and :x sd";
        msg = "%EF%BF%BD%3D%3BO%3A-%29%3A%3E%3E%3A%3E%3E%3A%3E%3E%23%3A-S%3A-O%3D%28%28%3A%7C%2F%3A%29%3A%3E%3A%3E%3A-%2F%3E%3AD%26lt%3B%3E%3AD%26lt%3B";
        msg = java.net.URLDecoder.decode(msg);
        //  msg = ":) :) :)";
//        msg = "&nbsp;:) :( ;) :D ;;) &gt;:D&lt; :-/ :x :&gt;&gt; :P :-* =(( :-OX( :&gt; B-) :-S #:-S &gt;:) :(( :)) :| /:) =)) O:-) :-B =; :-c";
        System.out.print(insertSmiley(msg, "http://192.168.0.50:8084/KrawlerESP/"));
    }
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">

    /**
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (SessionExpiredException xex) {
            KrawlerLog.op.warn("Service Exception deskeramob.java:" + xex.toString());
        }
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (SessionExpiredException xex) {
            KrawlerLog.op.warn("Service Exception deskeramob.java:" + xex.toString());
        }
    }

    /**
     * Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Short description";
    }

//    private String getUserid(Connection conn, String appid) throws SQLException, ServiceException {
//        String userid = "";
//        JSONObject jobj = new JSONObject();
//        String SELECT_USER_INFO = " Select userid from ideskeraauth where appnum = ?";
//        PreparedStatement pstmt = conn.prepareStatement(SELECT_USER_INFO);
//        pstmt.setString(1, appid);
//        ResultSet rs = pstmt.executeQuery();
//        if (rs.next()) {
//            userid = rs.getString("userid");
//        }
//        return userid;
//    }

    private String getCompanyID(Connection conn, String userid) throws SQLException, ServiceException {
        String companyid = "";
        JSONObject jobj = new JSONObject();
        String SELECT_USER_INFO = " Select companyid from users where userid = ?";
        PreparedStatement pstmt = conn.prepareStatement(SELECT_USER_INFO);
        pstmt.setString(1, userid);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) {
            companyid = rs.getString("companyid");
        }
        return companyid;
    }

    public String createNewCompany(Connection conn, HttpServletRequest request, HttpServletResponse response) throws ServiceException {
        String result = "";
        String userid2 = request.getParameter("u");
        String refid = request.getParameter("g");
        String emailid2 = request.getParameter("e");
        if (!(StringUtil.isNullOrEmpty(userid2) || StringUtil.isNullOrEmpty(emailid2))) {
            emailid2 = emailid2.replace(" ", "+");
            result = signupCompany(conn, emailid2, request.getParameter("cdomain"));
            if (!StringUtil.isNullOrEmpty(refid)) {
                setReference(conn, refid);
            }

            try {
                if (StringUtil.isNullOrEmpty(result)) {
                    SignupHandler.confirmSignup(conn, result, request);
                    String subdomain = CompanyHandler.getCompanySubdomainByUser(conn, userid2);
                    String uri = URLUtil.getPageURL(request, com.krawler.esp.web.resource.Links.loginpageFull, subdomain);
                    String pmsg = String.format(
                            "Hi %s,\n\nWelcome to Deskera and thanks for signing up!\n\n\nBookmark your login page - %s\n\nThis is the address where you'll log in to your account for now on\n\n - The Deskera Team\n",
                            userid2, uri, userid2);
                    String htmlmsg = String.format("<html><head><title>Deskera - Your Deskera Account</title></head>" +
                            "<style type='text/css'>a:link, a:visited, a:active {color: #03C;}" +
                            "body {font-family: Arial, Helvetica, sans-serif;color: #000;font-size: 13px;}" +
                            "</style><body><div><p>Hi <strong>%s</strong>,</p>" +
                            "<p>Welcome to Deskera and thanks for signing up!</p>" +
                            "<p>Access your Deskera account at: <a href='%s'>%s</a>" +
                            "<p>Read more about deskera:&nbsp;&nbsp;&nbsp;<a href='http://blog.deskera.com/'>Click here</a></p><br/><br/>" +
                            "<p>See you on Deskera!</p><p> - The Deskera Team</p>" +
                            "</div></body></html>", userid2, uri, uri, userid2);
                    try {
                        SendMailHandler.postMail(new String[]{emailid2}, "[Deskera] Welcome to Deskera", htmlmsg, pmsg, "admin@deskera.com");
                    } catch (MessagingException mE) {
                        Logger.getLogger(CompanyHandler.class.getName()).log(Level.SEVERE, "Message Exception While Email User Info", mE);
                    } catch (ConfigurationException cE) {
                        Logger.getLogger(CompanyHandler.class.getName()).log(Level.SEVERE, "Configuration Exception While Email User Info", cE);
                    }
                    uri += (Links.LOGIN+ "?first");
                    result = "{\"data\":[{\"success\":true,\"uri\":\"" + uri + "\"}]}";
                }
            } catch (Exception ex) {
                System.out.print(ex.getMessage());
                result = "{\"data\":[{\"success\": false}]}";
            }
        }
        return result;
    }

    public String signupCompany(Connection conn, String emailid, String companydomain)
            throws ServiceException {
        String result = "";
        /*if (SignupHandler.emailidIsAvailable(conn, emailid).equals("failure")) {
        result = "{\"data\":[{\"failure\":1}]}";
        }
        else*/
        if (SignupHandler.subdomainIsAvailable(conn, companydomain).equals("failure")) {
            result = "{\"data\":[{\"failure\":2}]}";
        }
        return result;
    }

    private String setReference(Connection conn, String refid) throws ServiceException {
        String result = "failure";
        int refNumber = 0;
        PreparedStatement pstmt = null;
        try {
            pstmt = conn.prepareStatement("SELECT numberofref FROM `references` WHERE refid=?;");
            pstmt.setString(1, refid);
            java.sql.ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                refNumber = rs.getInt(1) + 1;
                pstmt = conn.prepareStatement("UPDATE `references` SET numberofref=? WHERE refid=?;");
                pstmt.setInt(1, refNumber);
                pstmt.setString(2, refid);
                pstmt.executeUpdate();
                result = "success";
            }
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Signup.CreateRef", e);
        } finally {
            DbPool.closeStatement(pstmt);
        }
        return result;
    }

    private JSONObject getProjectResourceList(Connection conn, HttpServletRequest request) throws JSONException, ServiceException{
       String responseText;// = projdb.getProjectResources(conn,request.getParameter("id"));
        String projectid ;
       
        if(request.getParameter("id")==null){
           projectid = (String) request.getAttribute("id");
        }
        else
            projectid = request.getParameter("id");

        responseText = projdb.getProjectResources(conn,projectid);
        JSONObject jobj = new JSONObject();
        try{
                if (responseText.compareTo("{data:{}}") != 0) {
                    JSONObject resJOBJ = new JSONObject(responseText);
                    JSONArray jArray = resJOBJ.getJSONArray("data");
                    int prec = jArray.length();
                    if (prec > 0) {
                        for (int i = 0; i < prec; i++) {
                            JSONObject j = new JSONObject();
                            j.put("name", jArray.getJSONObject(i).getString("resourcename"));
                            j.put("id", jArray.getJSONObject(i).getString("resourceid"));
                            j.put("projectid",projectid);
                            jobj.append("data", j);
                        }
                    }
                }
         }catch (JSONException ex) {
                   String result = "{\"success\":false,\"data\":" + ex.getMessage() + "}";
                  // jobj.put("success", false);
                   //jobj.put("data", ex.getMessage());
         }
         return jobj;
    }
    private JSONObject getUserProjectList(Connection conn, String userid) throws JSONException, ServiceException{
    JSONObject projectList = new JSONObject(DashboardHandler.getProjectListMember(conn, userid, 20, 0));
                                JSONArray projArray = projectList.getJSONArray("data");
                                int prc = projArray.length();
                                JSONObject jobj = new JSONObject();
                                if (prc > 0) {
                                    for (int i = 0; i < projArray.length(); i++) {
                                        JSONObject tempProj = projArray.getJSONObject(i);
                                        if(tempProj.getInt("status") > 3){
                                            JSONObject j = new JSONObject();
                                            j.put("name", tempProj.getString("name"));
                                            j.put("id", tempProj.getString("id"));
                                            jobj.append("data", j);
                                        }
                                    }
                                }
                              return jobj;
    }
    private String authUser(Connection conn, String u, String p, String d) throws ServiceException {
        String result = "";
        try {
            JSONObject jobj = AuthHandler.verifyLogin(conn, u, p, d);
            if (jobj.has("success") && (jobj.get("success").equals(true))) {
//                String appId = UUID.randomUUID().toString();
//                String ADD_ENTRY = "INSERT INTO ideskeraauth(userid, appnum,udid) values (?, ?, ?)";
//                pstmt = conn.prepareStatement(ADD_ENTRY);
//                pstmt.setString(1, jobj.getString("lid"));
//                pstmt.setString(2, appId);
//                pstmt.setString(3, udid);
//                int cnt = pstmt.executeUpdate();
//                if (cnt > 0) {
                    JSONObject j = new JSONObject();
                    j.put("success", true);
//                    j.put("applicationid", appId);
                    j.put("userid", jobj.getString("lid"));
                    JSONObject retObj = new JSONObject();
                    retObj.append("data", j);
                    result = retObj.toString();
//                    conn.commit();
//                }
            } else {
                result = "{\"data\":[{\"success\":false}]}";
            }
        } catch (JSONException ex) {
            result = "{\"data\":[{\"success\":false,\"data\":" + ex.getMessage() + "}]}";
        }
        return result;
    }

    public static String createProject(Connection conn,
            HttpServletRequest request, String companyid, String subdomain, String userid)
            throws ServiceException {
        String status = "";
        DiskFileUpload fu = new DiskFileUpload();
        java.util.List fileItems = null;
        PreparedStatement pstmt = null;
        String imageName = "";
        try {
            fileItems = fu.parseRequest(request);
        } catch (FileUploadException e) {
            throw ServiceException.FAILURE("Admin.createProject", e);
        }

        java.util.HashMap arrParam = new java.util.HashMap();
        java.util.Iterator k = null;
        for (k = fileItems.iterator(); k.hasNext();) {
            FileItem fi1 = (FileItem) k.next();
            arrParam.put(fi1.getFieldName(), fi1.getString());
        }
        try {
            pstmt = conn.prepareStatement("select count(projectid) from project where companyid =? AND archived = 0");
            pstmt.setString(1, companyid);
            ResultSet rs = pstmt.executeQuery();
            int noProjects = 0;
            int maxProjects = 0;
            if (rs.next()) {
                noProjects = rs.getInt(1);
            }
            pstmt = conn.prepareStatement("select maxprojects from company where companyid =?");
            pstmt.setString(1, companyid);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                maxProjects = rs.getInt(1);
            }
            if (noProjects == maxProjects) {
                return "The maximum limit for projects for this company has already reached";
            }
        } catch (SQLException e) {
            throw ServiceException.FAILURE("ProfileHandler.getPersonalInfo", e);
        }
        try {
            String projectid = UUID.randomUUID().toString();
            String projName = StringUtil.serverHTMLStripper(arrParam.get("projectname").toString().replaceAll("[^\\w|\\s|'|\\-|\\[|\\]|\\(|\\)]", "").trim());
            String nickName = AdminServlet.makeNickName(conn, projName, 1);
            if (StringUtil.isNullOrEmpty(projName)) {
                status = "failure";
            } else {
                String qry = "INSERT INTO project (projectid,projectname,description,image,companyid, nickname) VALUES (?,?,?,?,?,?)";
                pstmt = conn.prepareStatement(qry);
                pstmt.setString(1, projectid);
                pstmt.setString(2, projName);
                pstmt.setString(3, arrParam.get("aboutproject").toString());
                pstmt.setString(4, imageName);
                pstmt.setString(5, companyid);
                pstmt.setString(6, nickName);
                int df = pstmt.executeUpdate();
                if(df != 0){
                    pstmt = conn.prepareStatement("INSERT INTO projectmembers (projectid, userid, status, inuseflag, planpermission) " +
                        "VALUES (?, ?, ?, ?, ?)");
                    pstmt.setString(1, projectid);
                    pstmt.setString(2, userid);
                    pstmt.setInt(3, 4);
                    pstmt.setBoolean(4, true);
                    pstmt.setInt(5, 0);
                    pstmt.executeUpdate();
                }
//                        /DbUtil.executeUpdate(conn,qry,new Object[] { projectid,projName,arrParam.get("aboutproject"), imageName,companyid, nickName});
                if (arrParam.get("image").toString().length() != 0) {
                    genericFileUpload uploader = new genericFileUpload();
                    uploader.doPost(fileItems, projectid, StorageHandler.GetProfileImgStorePath());
                    if (uploader.isUploaded()) {
                        pstmt = null;
//                                        DbUtil.executeUpdate(conn,
//                                                        "update project set image=? where projectid = ?",
//                                                        new Object[] {
//                                                                        ProfileImageServlet.ImgBasePath + projectid
//                                                                                        + uploader.getExt(), projectid });

                        pstmt = conn.prepareStatement("update project set image=? where projectid = ?");
                        pstmt.setString(1, ProfileImageServlet.ImgBasePath + projectid +"_200"+ uploader.getExt());
                        pstmt.setString(2, projectid);
                        pstmt.executeUpdate();
                        imageName = projectid + uploader.getExt();
                    }
                }
                com.krawler.esp.handlers.Forum.setStatusProject(conn, userid, projectid, 4, 0, "", subdomain);
                status = "success";
                AdminServlet.setDefaultWorkWeek(conn, projectid);
                conn.commit();
            }
        } catch (ConfigurationException e) {
            status = "failure";
            throw ServiceException.FAILURE("Admin.createProject", e);
        } catch (SQLException e) {
            status = "failure";
            throw ServiceException.FAILURE("Admin.createProject", e);
        }
        return status;
    }

    public String getAssiUnAssiProjctMembers(Connection conn, String projid) {
        String res = "";
        try {
            String query = "SELECT projectmembers.userid, username, 1 as assigned, status " + // assigned members
                    "FROM projectmembers INNER JOIN userlogin ON userlogin.userid = projectmembers.userid " +
                    "WHERE projectid = ? AND inuseflag = 1 AND status IN (3,4,5) " +
                    "UNION " +
                    "SELECT users.userid, username, 0 AS assigned, 0 as status FROM userlogin inner join users on users.userid = userlogin.userid  WHERE users.userid NOT IN " + // unassigned members
                    "(SELECT userid FROM projectmembers WHERE projectid = ? AND inuseflag = 1 AND status IN (3,4,5)) AND companyid = " +
                    "(SELECT companyid FROM project WHERE projectid = ?)";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setString(1, projid);
            pstmt.setString(2, projid);
            pstmt.setString(3, projid);
            ResultSet rs = pstmt.executeQuery();
            JSONObject jobj = new JSONObject();
            while (rs.next()) {
                JSONObject j = new JSONObject();
                j.put("userid", rs.getString("userid"));
                j.put("username", rs.getString("username"));
                j.put("assigned", rs.getString("assigned"));
                j.put("status", rs.getString("status"));
                jobj.append("data", j);
            }
            if (jobj.has("data")) {
                JSONObject temp = new JSONObject();
                temp.put("count", jobj.getJSONArray("data").length());
                jobj.append("membercount", temp);
                res = jobj.toString();
            } else {
                res = "{\"success\":true,\"data\":[]}";
            }
        } catch (Exception ex) {
            res = ex.getMessage();
        }
        return res;
    }

    public static String createUser(Connection conn, HttpServletRequest request, String companyid, String creatorID)
            throws ServiceException, SessionExpiredException {
        String status = "failure";
        DiskFileUpload fu = new DiskFileUpload();
        String userid = UUID.randomUUID().toString();
        String pwd = AuthHandler.generateNewPassword();
        String newPassSHA1 = AuthHandler.getSHA1(pwd);
        String username = request.getParameter("username").toString().replaceAll("[\\W]", "");
        String fname = request.getParameter("firstname").trim();
        String emailid = request.getParameter("email");
        emailid = emailid.trim().replace(" ", "+");

        if (StringUtil.isNullOrEmpty(username.trim()) || StringUtil.isNullOrEmpty(emailid.trim())) {
            return "failure";
        }
        if (SignupHandler.useridIsAvailable(conn, username, companyid).equalsIgnoreCase("failure")) {
            return "failure";
        }
//        if (SignupHandler.(conn, emailid, companyid).equalsIgnoreCase("failure")) {
//            return "failure";
//        }
        if (StringUtil.isNullOrEmpty(fname)) {
            fname = username;
        }
        PreparedStatement pstmt = null;
        username = StringUtil.serverHTMLStripper(username);
        fname = StringUtil.serverHTMLStripper(fname);
        String lname = StringUtil.serverHTMLStripper(request.getParameter("lastname").toString().trim());
        emailid = StringUtil.serverHTMLStripper(emailid);
        if (StringUtil.isNullOrEmpty(username) || StringUtil.isNullOrEmpty(fname) || StringUtil.isNullOrEmpty(lname) || StringUtil.isNullOrEmpty(emailid)) {
            status = "failure";
            return status;
        }
        DbUtil.executeUpdate(conn, "INSERT INTO userlogin(userid, username, password, authkey) values (?, ?, ?, ?)",
                new Object[]{userid, username, newPassSHA1, pwd});
        DbUtil.executeUpdate(
                conn,
                "INSERT INTO users (userid, fname, lname, emailid, contactno, address ,image, companyid) " + "VALUES (?,?,?,?,?,?,?,?)", new Object[]{
                    userid,  fname, lname, emailid, "", "", "", companyid
                });

        try {
            String uri = URLUtil.getPageURL(request, com.krawler.esp.web.resource.Links.loginpageFull, CompanyHandler.getCompanySubdomainByCompanyID(conn, companyid));
            pstmt = conn.prepareStatement("select username, fname, lname, emailid from users inner join userlogin on users.userid = userlogin.userid where users.userid = ?");
            pstmt.setString(1, creatorID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String fnameCreator = rs.getString("fname");
                String lnameCreator = rs.getString("lname");
                String fullnameCreator = (fnameCreator + " " + lnameCreator).trim();
                if (StringUtil.isNullOrEmpty(fnameCreator) && StringUtil.isNullOrEmpty(lnameCreator)) {
                    fullnameCreator = username;
                }
                String emailidCreator = rs.getString("emailid");
                String pmsg = String.format(
                        KWLErrorMsgs.msgMailInvite,
                        fname, fullnameCreator, username, pwd, uri, fullnameCreator);
                String companyname = AdminServlet.getCompanyname(conn, companyid);
                String htmlmsg = String.format(
                        KWLErrorMsgs.msgMailInviteUsernamePassword, fname, fullnameCreator, companyname,
                        username, pwd, uri, uri, fullnameCreator);
                try {
                    SendMailHandler.postMail(new String[]{emailid},
                            "[Deskera] Welcome to Deskera", htmlmsg, pmsg,
                            emailidCreator);
                } catch (ConfigurationException e) {
//                    e.printStackTrace();
                } catch (MessagingException e) {
//                    e.printStackTrace();
                }
                status = "{\"success\":true,\"userid\":\"" + userid + "\"}";
            }
            rs.close();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("ProfileHandler.getPersonalInfo", e);
        } finally {
            DbPool.closeStatement(pstmt);
        }
        return status;
    }
    
    public static String getDateOrdinal(int num){
      String[] suffix = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
      int m = num % 100;
      return suffix[(m > 10 && m < 20) ? 0 : (m % 10)];
    }
}
