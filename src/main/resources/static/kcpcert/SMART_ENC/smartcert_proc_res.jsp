<%@ page language="java" contentType="text/html;charset=euc-kr"%>
<%@ page import="java.util.Enumeration" %>
<%@ page import="kr.co.kcp.CT_CLI"%>
<%@ page import="java.net.URLDecoder"%>
<%@ include file="../cfg/cert_conf.jsp"%>
<%
    /* ============================================================================== */
    /* =   ���������� ���� �� ��ȣȭ ������                                         = */
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
    /* ============================================================================== */
%>
<%
    request.setCharacterEncoding ( "euc-kr" ) ;

    String site_cd       = "";
    String ordr_idxx     = "";

    String cert_no       = "";
    String enc_cert_data2 = "";
    String enc_info      = "";
    String enc_data      = "";
    String req_tx        = "";

    String tran_cd       = "";
    String res_cd        = "";
    String res_msg       = "";

    String dn_hash       = "";
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

            if( nmParam.equals( "ordr_idxx" ) )
            {
                ordr_idxx = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "res_cd" ) )
            {
                res_cd = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "req_tx" ) )
            {
                req_tx = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "cert_no" ) )
            {
                cert_no = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "enc_cert_data2" ) )
            {
                enc_cert_data2 = f_get_parm_str( valParam[i] );
            }

            if( nmParam.equals( "dn_hash" ) )
            {
                dn_hash = f_get_parm_str( valParam[i] );
            }
            // ��� �޽����� �ѱ� ������ URL decoding ������մϴ�.
            if( nmParam.equals( "res_msg" ) )
            {
                sbParam.append( "<input type=\"hidden\" name=\"" + nmParam + "\" value=\"" + URLDecoder.decode( valParam[i], "UTF-8" ) + "\"/>" );
            }
            else
            {
                sbParam.append( "<input type=\"hidden\" name=\"" + nmParam + "\" value=\"" + f_get_parm_str( valParam[i] ) + "\"/>" );
            }

        }
    }

    // ��� ó��
        if( res_cd.equals( "0000" ) )
        {
            // dn_hash ����
            // KCP �� ������ �帮�� dn_hash �� ����Ʈ �ڵ�, ��û��ȣ , ������ȣ�� �����Ͽ�
            // �ش� �������� �������� �����մϴ�
            if ( cc.checkValidHash( g_conf_ENC_KEY, dn_hash, ( site_cd + ordr_idxx + cert_no ) ) != true )
            {
                // ���� ���н� ó�� ����

                System.out.println("dn_hash ���� ��������");
                //cc = null; // ��ü �ݳ� ( ��ƾ Ż��ÿ��� ȣ�� )
            }

            // ������ DB ó�� ������ ����

            System.out.println(site_cd);
            System.out.println(cert_no);
            //System.out.println(enc_cert_data2); // ��ȣȭ v2 ����

            // ���������� ��ȣȭ �Լ�
            // �ش� �Լ��� ��ȣȭ�� enc_cert_data2 ��
            // site_cd �� cert_no �� ������ ��ȭȭ �ϴ� �Լ� �Դϴ�.
            // ���������� ��ȣȭ �Ȱ�쿡�� ���������͸� �����ü� �ֽ��ϴ�.
            cc.decryptEncCert( g_conf_ENC_KEY, site_cd, cert_no, enc_cert_data2 );
            //cc.setCharSetUtf8(); // ��ȣ�� ����� ���ڵ� ���� �޼��� ( UTF-8 ���ڵ� ���� �ּ��� �����Ͻñ� �ٶ��ϴ�.)

            System.out.println( "�̵���Ż� �ڵ�"    + cc.getKeyValue("comm_id"     ) ); // �̵���Ż� �ڵ�
            System.out.println( "��ȭ��ȣ"           + cc.getKeyValue("phone_no"    ) ); // ��ȭ��ȣ
            System.out.println( "�̸�"               + cc.getKeyValue("user_name"   ) ); // �̸�
            System.out.println( "�������"           + cc.getKeyValue("birth_day"   ) ); // �������
            System.out.println( "�����ڵ�"           + cc.getKeyValue("sex_code"    ) ); // �����ڵ�
            System.out.println( "��/�ܱ��� ���� "    + cc.getKeyValue("local_code"  ) ); // ��/�ܱ��� ����
            System.out.println( "CI"                 + cc.getKeyValue("ci"          ) ); // CI
            System.out.println( "DI �ߺ����� Ȯ�ΰ�" + cc.getKeyValue("di"          ) ); // DI �ߺ����� Ȯ�ΰ�
            System.out.println( "CI_URL"             + URLDecoder.decode( cc.getKeyValue("ci_url"      ) ) ); // CI URL ���ڵ� ��
            System.out.println( "DI_URL"             + URLDecoder.decode( cc.getKeyValue("di_url"      ) ) ); // DI URL ���ڵ� ��
            System.out.println( "������Ʈ ���̵�  "  + cc.getKeyValue("web_siteid"  ) ); // ��ȣȭ�� ������Ʈ ���̵�
            System.out.println( "��ȣȭ�� ����ڵ�"  + cc.getKeyValue("res_cd"      ) ); // ��ȣȭ�� ����ڵ�
            System.out.println( "��ȣȭ�� ����޽���"+ cc.getKeyValue("res_msg"     ) ); // ��ȣȭ�� ����޽���

        }
        else/*if( res_cd.equals( "0000" ) != true )*/
        {
            // ��������
        }

    cc = null; // ��ü �ݳ�
%>



<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" >
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=EUC-KR">
        <title>*** NHN KCP Online Payment System [Jsp Version] ***</title>
        <script type="text/javascript">
            window.onload=function()
            {
                try
                {
                    parent.auth_data( document.form_auth ); // �θ�â���� �� ����
                }
                catch(e)
                {
                    alert(e); // �������� �θ�â�� iframe �� ��ã�� �����
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