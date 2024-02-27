alter table SNSDingTalkEndpointVO
    add secret1 varchar(128) default '' null;

alter table SNSDingTalkAtPersonVO
    add lastOpDate1 timestamp ON UPDATE CURRENT_TIMESTAMP;

alter table SNSDingTalkAtPersonVO
    add createDate1 timestamp NOT NULL DEFAULT '0000-00-00 00:00:00';

alter table SNSDingTalkAtPersonVO
    add remark1 varchar(128) default '' null;


alter table SNSDingTalkEndpointVO
    add secret2 varchar(128) default '' null;

alter table SNSDingTalkAtPersonVO
    add lastOpDate2 timestamp ON UPDATE CURRENT_TIMESTAMP;

alter table SNSDingTalkAtPersonVO
    add createDate2 timestamp NOT NULL DEFAULT '0000-00-00 00:00:00';

alter table SNSDingTalkAtPersonVO
    add remark2 varchar(128) default '' null;