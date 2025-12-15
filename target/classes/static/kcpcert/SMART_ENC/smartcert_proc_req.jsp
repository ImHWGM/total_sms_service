<%@ page language="java" contentType="text/html;charset=euc-kr"%>
<%@ page import="java.util.Enumeration" %>
<%@ page import="kr.co.kcp.CT_CLI"%>
<%@ include file="../cfg/cert_conf.jsp"%>
<%
    /* ============================================================================== */
    /* =   ����â ȣ�� �� ���� ������                                               = */
    /* = -------------------------------------------------------------------------- = */
    /* =   �ش� �������� �ݵ�� ������ ������ ���ε� �Ǿ�� �ϸ�                    = */
    /* =   ������ �������� ����Ͻñ� �ٶ��ϴ�.                                     = */
    /* ============================================================================== */
%>
<%!
    /* ============================================================================== */
    /* =   null ���� ó���ϴ� �޼ҵ�                                                = */
    /* = -------------------------------------------------------------------------- = */
    public String f_get_parm_str( String val )
    {
        if ( val == null ) val = "";
        return  val;
    }


    //!!�߿� �ش� �Լ��� year, month, day ������ null �� ��� 00 ���� ġȯ�մϴ�
    public String f_get_parm_int( String val )
    {
        String ret_val = "";

        if ( val == null      ) val = "00";
        if ( val.equals( "" ) ) val = "00";

        ret_val = val.length() == 1? ("0" + val) : val;

        return  ret_val;
    }
    /* ============================================================================== */
%>
<%
    request.setCharacterEncoding ( "euc-kr" ) ;

    String req_tx        = "";

    String site_cd       = "";
    String ordr_idxx     = "";

    String year          = "";
    String month         = "";
    String day           = "";
    String user_name     = "";
    String sex_code      = "";
    String local_code    = "";

    String web_siteid    = "";
    String web_siteid_hashYN = "";
    String cert_able_yn  = "";

    String up_hash       = "";
	/*------------------------------------------------------------------------*/
    /*  :: ��ü �Ķ���� �����                                               */
    /*------------------------------------------------------------------------*/
    StringBuffer sbParam = new StringBuffer();
    CT_CLI       cc      = new CT_CLI();
	//cc.setCharSetUtf8(); // UTF-8 ó��

    // request �� �Ѿ�� �� ó��
    Enumeration params = request.getParameterNames();
    while(params.hasMoreElements())
    {
         String nmParam = (String) params.nextElement();
         String valParam[] = request.getParameterValues(nmParam);

        for(int i = 0; i < valParam.length;i++)
        {
            if( nmParam.equals( "site_cd" ) )
            {
                site_cd = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "req_tx" ) )
            {
                req_tx = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "ordr_idxx" ) )
            {
                ordr_idxx = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "user_name" ) )
            {
                user_name = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "year" ) )
            {
                year = f_get_parm_int( valParam[i] );
            }

            if( nmParam.equals( "month" ) )
            {
                month = f_get_parm_int( valParam[i] );
            }

            if( nmParam.equals( "day" ) )
            {
                day = f_get_parm_int( valParam[i] );
            }

            if( nmParam.equals( "sex_code" ) )
            {
                sex_code = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "local_code" ) )
            {
                local_code = f_get_parm_str( valParam[i] );
            }
            if( nmParam.equals( "web_siteid_hashYN" ) )
            {
                web_siteid_hashYN = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "web_siteid" ) )
            {
                web_siteid = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "cert_able_yn" ) )
            {
                cert_able_yn = f_get_parm_str( valParam[i] );
            }

            // ����â���� �ѱ�� form ������ ���� �ʵ�
            sbParam.append( "<input type=\"hidden\" name=\"" + nmParam + "\" value=\"" + f_get_parm_str( valParam[i] ) + "\"/>" );
        }
    }

    if ( req_tx.equals( "cert" ) )
    {
        // !!up_hash ������ ������ ���� ����
        // year , month , day �� ��� �ִ� ��� "00" , "00" , "00" ���� ������ �˴ϴ�
        // �׿��� ���� ���� ��� ""(null) �� �����Ͻø� �˴ϴ�.
        // up_hash ������ ������ site_cd �� ordr_idxx �� �ʼ� ���Դϴ�.
        if( cert_able_yn.equals( "Y" ) )
        {
            up_hash = cc.makeHashData( g_conf_ENC_KEY, site_cd   +
                                       ordr_idxx +
                                       ( web_siteid_hashYN.equals( "Y" )? web_siteid:"" ) +
                                       ""   +
                                       "00" +
                                       "00" +
                                       "00" +
                                       ""   +
                                       ""
                                      );
        }
        else
        {
            up_hash = cc.makeHashData( g_conf_ENC_KEY, site_cd   +
                                       ordr_idxx +
                                       ( web_siteid_hashYN.equals( "Y" )? web_siteid:"" ) +
                                       user_name +
                                       year      +
                                       month     +
                                       day       +
                                       sex_code  +
                                       local_code
                                      );
        }

        // ����â���� �ѱ�� form ������ ���� �ʵ� ( up_hash )
        sbParam.append( "<input type=\"hidden\" name=\"up_hash\" value=\"" + up_hash + "\"/>" );

        // KCP ����Ȯ�� ���̺귯�� ���� ����
        sbParam.append( "<input type=\"hidden\" name=\"kcp_cert_lib_ver\" value=\"" + cc.getKCPLibVer() + "\"/>" );

        cc = null; // ��ü ����
    }
%>



<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" >
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=EUC-KR">
        <title>*** NHN KCP Online Payment System [Jsp Version] ***</title>
        <script type="text/javascript">
            window.onload=function()
            {
                cert_page();
            }

			// ���� ��û �� ȣ�� �Լ�
            function cert_page()
            {
                var frm = document.form_auth;

				if ( ( frm.req_tx.value == "auth" || frm.req_tx.value == "otp_auth" ) )
                {
                    frm.action="./smartcert_proc_res.jsp";

                    // MOBILE
                    if( ( navigator.userAgent.indexOf("Android") > - 1 || navigator.userAgent.indexOf("iPhone") > - 1 ) )
                    {
                        self.name="kcp_cert";
                    }
                    // PC
					else
					{
					    frm.target="kcp_cert";
					}

                    frm.submit();

                    window.close();
                }

				else if ( frm.req_tx.value == "cert" )
                {

                    if( ( navigator.userAgent.indexOf("Android") > - 1 || navigator.userAgent.indexOf("iPhone") > - 1 ) ) // ����Ʈ���� ���
                    {
                        parent.document.form_auth.veri_up_hash.value = frm.up_hash.value; // up_hash ������ ������ ���� �ʵ�
						self.name="auth_popup";
                    }
					else // ����Ʈ�� �ƴҶ�
					{
	                    opener.document.form_auth.veri_up_hash.value = frm.up_hash.value; // up_hash ������ ������ ���� �ʵ�
					}
                    frm.action="<%= g_conf_gw_url %>";
                    frm.submit();
                }
			}

        </script>
    </head>
    <body oncontextmenu="return false;" ondragstart="return false;" onselectstart="return false;">
        <form name="form_auth" method="post">
            <%= sbParam.toString() %>
        </form>
    </body>
</html>