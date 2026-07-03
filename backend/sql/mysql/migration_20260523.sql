USE youlai_boot;

-- ============================================================
-- Flowable 7.2.0 BPMN 流程引擎内部表迁移保护
-- 说明：Docker fresh init 会顺序执行 jiayuan_boot.sql 和本迁移；若全量脚本已建表，这里跳过，避免重复索引/约束报错。
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_flowable_schema_if_missing;
DELIMITER //
CREATE PROCEDURE migrate_flowable_schema_if_missing()
BEGIN
    DECLARE v_flowable_table_count INT DEFAULT 0;
    DECLARE v_expected_flowable_table_count INT DEFAULT 32;
    DECLARE v_flowable_metadata_count INT DEFAULT 0;

    SELECT COUNT(*)
    INTO v_flowable_table_count
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'ACT_GE_PROPERTY',
          'ACT_GE_BYTEARRAY',
          'ACT_RU_ENTITYLINK',
          'ACT_HI_ENTITYLINK',
          'ACT_RU_IDENTITYLINK',
          'ACT_HI_IDENTITYLINK',
          'ACT_RU_JOB',
          'ACT_RU_TIMER_JOB',
          'ACT_RU_SUSPENDED_JOB',
          'ACT_RU_DEADLETTER_JOB',
          'ACT_RU_HISTORY_JOB',
          'ACT_RU_EXTERNAL_JOB',
          'FLW_RU_BATCH',
          'FLW_RU_BATCH_PART',
          'ACT_RU_TASK',
          'ACT_HI_TASKINST',
          'ACT_HI_TSK_LOG',
          'ACT_RU_VARIABLE',
          'ACT_HI_VARINST',
          'ACT_RU_EVENT_SUBSCR',
          'ACT_RE_DEPLOYMENT',
          'ACT_RE_MODEL',
          'ACT_RU_EXECUTION',
          'ACT_RE_PROCDEF',
          'ACT_EVT_LOG',
          'ACT_PROCDEF_INFO',
          'ACT_RU_ACTINST',
          'ACT_HI_PROCINST',
          'ACT_HI_ACTINST',
          'ACT_HI_DETAIL',
          'ACT_HI_COMMENT',
          'ACT_HI_ATTACHMENT'
      );

    IF v_flowable_table_count = 0 THEN
        -- ============================================================
        -- Flowable 7.2.0 BPMN 流程引擎内部表
        -- 来源：Flowable 官方 MySQL create scripts（common、engine、history）
        -- ============================================================

        -- org/flowable/common/db/create/flowable.mysql.create.common.sql
        CREATE TABLE IF NOT EXISTS ACT_GE_PROPERTY (
            NAME_ varchar(64),
            VALUE_ varchar(300),
            REV_ integer,
            primary key (NAME_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_GE_BYTEARRAY (
            ID_ varchar(64),
            REV_ integer,
            NAME_ varchar(255),
            DEPLOYMENT_ID_ varchar(64),
            BYTES_ LONGBLOB,
            GENERATED_ TINYINT,
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        INSERT IGNORE INTO ACT_GE_PROPERTY
        values ('common.schema.version', '7.2.0.2', 1);

        INSERT IGNORE INTO ACT_GE_PROPERTY
        values ('next.dbid', '1', 1);


        CREATE TABLE IF NOT EXISTS ACT_RU_ENTITYLINK (
            ID_ varchar(64),
            REV_ integer,
            CREATE_TIME_ datetime(3),
            LINK_TYPE_ varchar(255),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            PARENT_ELEMENT_ID_ varchar(255),
            REF_SCOPE_ID_ varchar(255),
            REF_SCOPE_TYPE_ varchar(255),
            REF_SCOPE_DEFINITION_ID_ varchar(255),
            ROOT_SCOPE_ID_ varchar(255),
            ROOT_SCOPE_TYPE_ varchar(255),
            HIERARCHY_TYPE_ varchar(255),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_ENT_LNK_SCOPE on ACT_RU_ENTITYLINK(SCOPE_ID_, SCOPE_TYPE_, LINK_TYPE_);
        CREATE INDEX ACT_IDX_ENT_LNK_REF_SCOPE on ACT_RU_ENTITYLINK(REF_SCOPE_ID_, REF_SCOPE_TYPE_, LINK_TYPE_);
        CREATE INDEX ACT_IDX_ENT_LNK_ROOT_SCOPE on ACT_RU_ENTITYLINK(ROOT_SCOPE_ID_, ROOT_SCOPE_TYPE_, LINK_TYPE_);
        CREATE INDEX ACT_IDX_ENT_LNK_SCOPE_DEF on ACT_RU_ENTITYLINK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_, LINK_TYPE_);

        CREATE TABLE IF NOT EXISTS ACT_HI_ENTITYLINK (
            ID_ varchar(64),
            LINK_TYPE_ varchar(255),
            CREATE_TIME_ datetime(3),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            PARENT_ELEMENT_ID_ varchar(255),
            REF_SCOPE_ID_ varchar(255),
            REF_SCOPE_TYPE_ varchar(255),
            REF_SCOPE_DEFINITION_ID_ varchar(255),
            ROOT_SCOPE_ID_ varchar(255),
            ROOT_SCOPE_TYPE_ varchar(255),
            HIERARCHY_TYPE_ varchar(255),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_HI_ENT_LNK_SCOPE on ACT_HI_ENTITYLINK(SCOPE_ID_, SCOPE_TYPE_, LINK_TYPE_);
        CREATE INDEX ACT_IDX_HI_ENT_LNK_REF_SCOPE on ACT_HI_ENTITYLINK(REF_SCOPE_ID_, REF_SCOPE_TYPE_, LINK_TYPE_);
        CREATE INDEX ACT_IDX_HI_ENT_LNK_ROOT_SCOPE on ACT_HI_ENTITYLINK(ROOT_SCOPE_ID_, ROOT_SCOPE_TYPE_, LINK_TYPE_);
        CREATE INDEX ACT_IDX_HI_ENT_LNK_SCOPE_DEF on ACT_HI_ENTITYLINK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_, LINK_TYPE_);


        CREATE TABLE IF NOT EXISTS ACT_RU_IDENTITYLINK (
            ID_ varchar(64),
            REV_ integer,
            GROUP_ID_ varchar(255),
            TYPE_ varchar(255),
            USER_ID_ varchar(255),
            TASK_ID_ varchar(64),
            PROC_INST_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_IDENT_LNK_USER on ACT_RU_IDENTITYLINK(USER_ID_);
        CREATE INDEX ACT_IDX_IDENT_LNK_GROUP on ACT_RU_IDENTITYLINK(GROUP_ID_);
        CREATE INDEX ACT_IDX_IDENT_LNK_SCOPE on ACT_RU_IDENTITYLINK(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_IDENT_LNK_SUB_SCOPE on ACT_RU_IDENTITYLINK(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_IDENT_LNK_SCOPE_DEF on ACT_RU_IDENTITYLINK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

        CREATE TABLE IF NOT EXISTS ACT_HI_IDENTITYLINK (
            ID_ varchar(64),
            GROUP_ID_ varchar(255),
            TYPE_ varchar(255),
            USER_ID_ varchar(255),
            TASK_ID_ varchar(64),
            CREATE_TIME_ datetime(3),
            PROC_INST_ID_ varchar(64),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_HI_IDENT_LNK_USER on ACT_HI_IDENTITYLINK(USER_ID_);
        CREATE INDEX ACT_IDX_HI_IDENT_LNK_SCOPE on ACT_HI_IDENTITYLINK(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_HI_IDENT_LNK_SUB_SCOPE on ACT_HI_IDENTITYLINK(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_HI_IDENT_LNK_SCOPE_DEF on ACT_HI_IDENTITYLINK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);


        CREATE TABLE IF NOT EXISTS ACT_RU_JOB (
            ID_ varchar(64) NOT NULL,
            REV_ integer,
            CATEGORY_ varchar(255),
            TYPE_ varchar(255) NOT NULL,
            LOCK_EXP_TIME_ timestamp(3) NULL,
            LOCK_OWNER_ varchar(255),
            EXCLUSIVE_ boolean,
            EXECUTION_ID_ varchar(64),
            PROCESS_INSTANCE_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            ELEMENT_ID_ varchar(255),
            ELEMENT_NAME_ varchar(255),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            CORRELATION_ID_ varchar(255),
            RETRIES_ integer,
            EXCEPTION_STACK_ID_ varchar(64),
            EXCEPTION_MSG_ varchar(4000),
            DUEDATE_ timestamp(3) NULL,
            REPEAT_ varchar(255),
            HANDLER_TYPE_ varchar(255),
            HANDLER_CFG_ varchar(4000),
            CUSTOM_VALUES_ID_ varchar(64),
            CREATE_TIME_ timestamp(3) NULL,
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RU_TIMER_JOB (
            ID_ varchar(64) NOT NULL,
            REV_ integer,
            CATEGORY_ varchar(255),
            TYPE_ varchar(255) NOT NULL,
            LOCK_EXP_TIME_ timestamp(3) NULL,
            LOCK_OWNER_ varchar(255),
            EXCLUSIVE_ boolean,
            EXECUTION_ID_ varchar(64),
            PROCESS_INSTANCE_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            ELEMENT_ID_ varchar(255),
            ELEMENT_NAME_ varchar(255),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            CORRELATION_ID_ varchar(255),
            RETRIES_ integer,
            EXCEPTION_STACK_ID_ varchar(64),
            EXCEPTION_MSG_ varchar(4000),
            DUEDATE_ timestamp(3) NULL,
            REPEAT_ varchar(255),
            HANDLER_TYPE_ varchar(255),
            HANDLER_CFG_ varchar(4000),
            CUSTOM_VALUES_ID_ varchar(64),
            CREATE_TIME_ timestamp(3) NULL,
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RU_SUSPENDED_JOB (
            ID_ varchar(64) NOT NULL,
            REV_ integer,
            CATEGORY_ varchar(255),
            TYPE_ varchar(255) NOT NULL,
            EXCLUSIVE_ boolean,
            EXECUTION_ID_ varchar(64),
            PROCESS_INSTANCE_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            ELEMENT_ID_ varchar(255),
            ELEMENT_NAME_ varchar(255),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            CORRELATION_ID_ varchar(255),
            RETRIES_ integer,
            EXCEPTION_STACK_ID_ varchar(64),
            EXCEPTION_MSG_ varchar(4000),
            DUEDATE_ timestamp(3) NULL,
            REPEAT_ varchar(255),
            HANDLER_TYPE_ varchar(255),
            HANDLER_CFG_ varchar(4000),
            CUSTOM_VALUES_ID_ varchar(64),
            CREATE_TIME_ timestamp(3) NULL,
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RU_DEADLETTER_JOB (
            ID_ varchar(64) NOT NULL,
            REV_ integer,
            CATEGORY_ varchar(255),
            TYPE_ varchar(255) NOT NULL,
            EXCLUSIVE_ boolean,
            EXECUTION_ID_ varchar(64),
            PROCESS_INSTANCE_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            ELEMENT_ID_ varchar(255),
            ELEMENT_NAME_ varchar(255),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            CORRELATION_ID_ varchar(255),
            EXCEPTION_STACK_ID_ varchar(64),
            EXCEPTION_MSG_ varchar(4000),
            DUEDATE_ timestamp(3) NULL,
            REPEAT_ varchar(255),
            HANDLER_TYPE_ varchar(255),
            HANDLER_CFG_ varchar(4000),
            CUSTOM_VALUES_ID_ varchar(64),
            CREATE_TIME_ timestamp(3) NULL,
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RU_HISTORY_JOB (
            ID_ varchar(64) NOT NULL,
            REV_ integer,
            LOCK_EXP_TIME_ timestamp(3) NULL,
            LOCK_OWNER_ varchar(255),
            RETRIES_ integer,
            EXCEPTION_STACK_ID_ varchar(64),
            EXCEPTION_MSG_ varchar(4000),
            HANDLER_TYPE_ varchar(255),
            HANDLER_CFG_ varchar(4000),
            CUSTOM_VALUES_ID_ varchar(64),
            ADV_HANDLER_CFG_ID_ varchar(64),
            CREATE_TIME_ timestamp(3) NULL,
            SCOPE_TYPE_ varchar(255),
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RU_EXTERNAL_JOB (
            ID_ varchar(64) NOT NULL,
            REV_ integer,
            CATEGORY_ varchar(255),
            TYPE_ varchar(255) NOT NULL,
            LOCK_EXP_TIME_ timestamp(3) NULL,
            LOCK_OWNER_ varchar(255),
            EXCLUSIVE_ boolean,
            EXECUTION_ID_ varchar(64),
            PROCESS_INSTANCE_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            ELEMENT_ID_ varchar(255),
            ELEMENT_NAME_ varchar(255),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            CORRELATION_ID_ varchar(255),
            RETRIES_ integer,
            EXCEPTION_STACK_ID_ varchar(64),
            EXCEPTION_MSG_ varchar(4000),
            DUEDATE_ timestamp(3) NULL,
            REPEAT_ varchar(255),
            HANDLER_TYPE_ varchar(255),
            HANDLER_CFG_ varchar(4000),
            CUSTOM_VALUES_ID_ varchar(64),
            CREATE_TIME_ timestamp(3) NULL,
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_JOB_EXCEPTION_STACK_ID on ACT_RU_JOB(EXCEPTION_STACK_ID_);
        CREATE INDEX ACT_IDX_JOB_CUSTOM_VALUES_ID on ACT_RU_JOB(CUSTOM_VALUES_ID_);
        CREATE INDEX ACT_IDX_JOB_CORRELATION_ID on ACT_RU_JOB(CORRELATION_ID_);

        CREATE INDEX ACT_IDX_TIMER_JOB_EXCEPTION_STACK_ID on ACT_RU_TIMER_JOB(EXCEPTION_STACK_ID_);
        CREATE INDEX ACT_IDX_TIMER_JOB_CUSTOM_VALUES_ID on ACT_RU_TIMER_JOB(CUSTOM_VALUES_ID_);
        CREATE INDEX ACT_IDX_TIMER_JOB_CORRELATION_ID on ACT_RU_TIMER_JOB(CORRELATION_ID_);
        CREATE INDEX ACT_IDX_TIMER_JOB_DUEDATE on ACT_RU_TIMER_JOB(DUEDATE_);

        CREATE INDEX ACT_IDX_SUSPENDED_JOB_EXCEPTION_STACK_ID on ACT_RU_SUSPENDED_JOB(EXCEPTION_STACK_ID_);
        CREATE INDEX ACT_IDX_SUSPENDED_JOB_CUSTOM_VALUES_ID on ACT_RU_SUSPENDED_JOB(CUSTOM_VALUES_ID_);
        CREATE INDEX ACT_IDX_SUSPENDED_JOB_CORRELATION_ID on ACT_RU_SUSPENDED_JOB(CORRELATION_ID_);

        CREATE INDEX ACT_IDX_DEADLETTER_JOB_EXCEPTION_STACK_ID on ACT_RU_DEADLETTER_JOB(EXCEPTION_STACK_ID_);
        CREATE INDEX ACT_IDX_DEADLETTER_JOB_CUSTOM_VALUES_ID on ACT_RU_DEADLETTER_JOB(CUSTOM_VALUES_ID_);
        CREATE INDEX ACT_IDX_DEADLETTER_JOB_CORRELATION_ID on ACT_RU_DEADLETTER_JOB(CORRELATION_ID_);

        CREATE INDEX ACT_IDX_EXTERNAL_JOB_EXCEPTION_STACK_ID on ACT_RU_EXTERNAL_JOB(EXCEPTION_STACK_ID_);
        CREATE INDEX ACT_IDX_EXTERNAL_JOB_CUSTOM_VALUES_ID on ACT_RU_EXTERNAL_JOB(CUSTOM_VALUES_ID_);
        CREATE INDEX ACT_IDX_EXTERNAL_JOB_CORRELATION_ID on ACT_RU_EXTERNAL_JOB(CORRELATION_ID_);

        ALTER TABLE ACT_RU_JOB
            add constraint ACT_FK_JOB_EXCEPTION
            foreign key (EXCEPTION_STACK_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_JOB
            add constraint ACT_FK_JOB_CUSTOM_VALUES
            foreign key (CUSTOM_VALUES_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_TIMER_JOB
            add constraint ACT_FK_TIMER_JOB_EXCEPTION
            foreign key (EXCEPTION_STACK_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_TIMER_JOB
            add constraint ACT_FK_TIMER_JOB_CUSTOM_VALUES
            foreign key (CUSTOM_VALUES_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_SUSPENDED_JOB
            add constraint ACT_FK_SUSPENDED_JOB_EXCEPTION
            foreign key (EXCEPTION_STACK_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_SUSPENDED_JOB
            add constraint ACT_FK_SUSPENDED_JOB_CUSTOM_VALUES
            foreign key (CUSTOM_VALUES_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_DEADLETTER_JOB
            add constraint ACT_FK_DEADLETTER_JOB_EXCEPTION
            foreign key (EXCEPTION_STACK_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_DEADLETTER_JOB
            add constraint ACT_FK_DEADLETTER_JOB_CUSTOM_VALUES
            foreign key (CUSTOM_VALUES_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_EXTERNAL_JOB
            add constraint ACT_FK_EXTERNAL_JOB_EXCEPTION
            foreign key (EXCEPTION_STACK_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RU_EXTERNAL_JOB
            add constraint ACT_FK_EXTERNAL_JOB_CUSTOM_VALUES
            foreign key (CUSTOM_VALUES_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        CREATE INDEX ACT_IDX_JOB_SCOPE on ACT_RU_JOB(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_JOB_SUB_SCOPE on ACT_RU_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_JOB_SCOPE_DEF on ACT_RU_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

        CREATE INDEX ACT_IDX_TJOB_SCOPE on ACT_RU_TIMER_JOB(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_TJOB_SUB_SCOPE on ACT_RU_TIMER_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_TJOB_SCOPE_DEF on ACT_RU_TIMER_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

        CREATE INDEX ACT_IDX_SJOB_SCOPE on ACT_RU_SUSPENDED_JOB(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_SJOB_SUB_SCOPE on ACT_RU_SUSPENDED_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_SJOB_SCOPE_DEF on ACT_RU_SUSPENDED_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

        CREATE INDEX ACT_IDX_DJOB_SCOPE on ACT_RU_DEADLETTER_JOB(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_DJOB_SUB_SCOPE on ACT_RU_DEADLETTER_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_DJOB_SCOPE_DEF on ACT_RU_DEADLETTER_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

        CREATE INDEX ACT_IDX_EJOB_SCOPE on ACT_RU_EXTERNAL_JOB(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_EJOB_SUB_SCOPE on ACT_RU_EXTERNAL_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_EJOB_SCOPE_DEF on ACT_RU_EXTERNAL_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

        CREATE TABLE IF NOT EXISTS FLW_RU_BATCH (
            ID_ varchar(64) not null,
            REV_ integer,
            TYPE_ varchar(64) not null,
            SEARCH_KEY_ varchar(255),
            SEARCH_KEY2_ varchar(255),
            CREATE_TIME_ datetime(3) not null,
            COMPLETE_TIME_ datetime(3),
            STATUS_ varchar(255),
            BATCH_DOC_ID_ varchar(64),
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS FLW_RU_BATCH_PART (
            ID_ varchar(64) not null,
            REV_ integer,
            BATCH_ID_ varchar(64),
            TYPE_ varchar(64) not null,
            SCOPE_ID_ varchar(64),
            SUB_SCOPE_ID_ varchar(64),
            SCOPE_TYPE_ varchar(64),
            SEARCH_KEY_ varchar(255),
            SEARCH_KEY2_ varchar(255),
            CREATE_TIME_ datetime(3) not null,
            COMPLETE_TIME_ datetime(3),
            STATUS_ varchar(255),
            RESULT_DOC_ID_ varchar(64),
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX FLW_IDX_BATCH_PART on FLW_RU_BATCH_PART(BATCH_ID_);

        ALTER TABLE FLW_RU_BATCH_PART
            add constraint FLW_FK_BATCH_PART_PARENT
            foreign key (BATCH_ID_)
            references FLW_RU_BATCH (ID_);

        CREATE TABLE IF NOT EXISTS ACT_RU_TASK (
            ID_ varchar(64),
            REV_ integer,
            EXECUTION_ID_ varchar(64),
            PROC_INST_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            TASK_DEF_ID_ varchar(64),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            PROPAGATED_STAGE_INST_ID_ varchar(255),
            STATE_ varchar(255),
            NAME_ varchar(255),
            PARENT_TASK_ID_ varchar(64),
            DESCRIPTION_ varchar(4000),
            TASK_DEF_KEY_ varchar(255),
            OWNER_ varchar(255),
            ASSIGNEE_ varchar(255),
            DELEGATION_ varchar(64),
            PRIORITY_ integer,
            CREATE_TIME_ timestamp(3) NULL,
            IN_PROGRESS_TIME_ datetime(3),
            IN_PROGRESS_STARTED_BY_ varchar(255),
            CLAIM_TIME_ datetime(3),
            CLAIMED_BY_ varchar(255),
            SUSPENDED_TIME_ datetime(3),
            SUSPENDED_BY_ varchar(255),
            IN_PROGRESS_DUE_DATE_ datetime(3),
            DUE_DATE_ datetime(3),
            CATEGORY_ varchar(255),
            SUSPENSION_STATE_ integer,
            TENANT_ID_ varchar(255) default '',
            FORM_KEY_ varchar(255),
            IS_COUNT_ENABLED_ TINYINT,
            VAR_COUNT_ integer,
            ID_LINK_COUNT_ integer,
            SUB_TASK_COUNT_ integer,
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_TASK_CREATE on ACT_RU_TASK(CREATE_TIME_);
        CREATE INDEX ACT_IDX_TASK_SCOPE on ACT_RU_TASK(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_TASK_SUB_SCOPE on ACT_RU_TASK(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_TASK_SCOPE_DEF on ACT_RU_TASK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

        CREATE TABLE IF NOT EXISTS ACT_HI_TASKINST (
            ID_ varchar(64) not null,
            REV_ integer default 1,
            PROC_DEF_ID_ varchar(64),
            TASK_DEF_ID_ varchar(64),
            TASK_DEF_KEY_ varchar(255),
            PROC_INST_ID_ varchar(64),
            EXECUTION_ID_ varchar(64),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            PROPAGATED_STAGE_INST_ID_ varchar(255),
            STATE_ varchar(255),
            NAME_ varchar(255),
            PARENT_TASK_ID_ varchar(64),
            DESCRIPTION_ varchar(4000),
            OWNER_ varchar(255),
            ASSIGNEE_ varchar(255),
            START_TIME_ datetime(3) not null,
            IN_PROGRESS_TIME_ datetime(3),
            IN_PROGRESS_STARTED_BY_ varchar(255),
            CLAIM_TIME_ datetime(3),
            CLAIMED_BY_ varchar(255),
            SUSPENDED_TIME_ datetime(3),
            SUSPENDED_BY_ varchar(255),
            END_TIME_ datetime(3),
            COMPLETED_BY_ varchar(255),
            DURATION_ bigint,
            DELETE_REASON_ varchar(4000),
            PRIORITY_ integer,
            IN_PROGRESS_DUE_DATE_ datetime(3),
            DUE_DATE_ datetime(3),
            FORM_KEY_ varchar(255),
            CATEGORY_ varchar(255),
            TENANT_ID_ varchar(255) default '',
            LAST_UPDATED_TIME_ datetime(3),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_HI_TSK_LOG (
            ID_ bigint auto_increment,
            TYPE_ varchar(64),
            TASK_ID_ varchar(64) not null,
            TIME_STAMP_ timestamp(3) not null,
            USER_ID_ varchar(255),
            DATA_ varchar(4000),
            EXECUTION_ID_ varchar(64),
            PROC_INST_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            SCOPE_ID_ varchar(255),
            SCOPE_DEFINITION_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_HI_TASK_SCOPE on ACT_HI_TASKINST(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_HI_TASK_SUB_SCOPE on ACT_HI_TASKINST(SUB_SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_HI_TASK_SCOPE_DEF on ACT_HI_TASKINST(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_ACT_HI_TSK_LOG_TASK on ACT_HI_TSK_LOG(TASK_ID_);


        CREATE TABLE IF NOT EXISTS ACT_RU_VARIABLE (
            ID_ varchar(64) not null,
            REV_ integer,
            TYPE_ varchar(255) not null,
            NAME_ varchar(255) not null,
            EXECUTION_ID_ varchar(64),
            PROC_INST_ID_ varchar(64),
            TASK_ID_ varchar(64),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            BYTEARRAY_ID_ varchar(64),
            DOUBLE_ double,
            LONG_ bigint,
            TEXT_ varchar(4000),
            TEXT2_ varchar(4000),
            META_INFO_ varchar(4000),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_RU_VAR_SCOPE_ID_TYPE on ACT_RU_VARIABLE(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_RU_VAR_SUB_ID_TYPE on ACT_RU_VARIABLE(SUB_SCOPE_ID_, SCOPE_TYPE_);

        ALTER TABLE ACT_RU_VARIABLE
            add constraint ACT_FK_VAR_BYTEARRAY
            foreign key (BYTEARRAY_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        CREATE TABLE IF NOT EXISTS ACT_HI_VARINST (
            ID_ varchar(64) not null,
            REV_ integer default 1,
            PROC_INST_ID_ varchar(64),
            EXECUTION_ID_ varchar(64),
            TASK_ID_ varchar(64),
            NAME_ varchar(255) not null,
            VAR_TYPE_ varchar(100),
            SCOPE_ID_ varchar(255),
            SUB_SCOPE_ID_ varchar(255),
            SCOPE_TYPE_ varchar(255),
            BYTEARRAY_ID_ varchar(64),
            DOUBLE_ double,
            LONG_ bigint,
            TEXT_ varchar(4000),
            TEXT2_ varchar(4000),
            META_INFO_ varchar(4000),
            CREATE_TIME_ datetime(3),
            LAST_UPDATED_TIME_ datetime(3),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_HI_PROCVAR_NAME_TYPE on ACT_HI_VARINST(NAME_, VAR_TYPE_);
        CREATE INDEX ACT_IDX_HI_VAR_SCOPE_ID_TYPE on ACT_HI_VARINST(SCOPE_ID_, SCOPE_TYPE_);
        CREATE INDEX ACT_IDX_HI_VAR_SUB_ID_TYPE on ACT_HI_VARINST(SUB_SCOPE_ID_, SCOPE_TYPE_);


        CREATE TABLE IF NOT EXISTS ACT_RU_EVENT_SUBSCR (
            ID_ varchar(64) not null,
            REV_ integer,
            EVENT_TYPE_ varchar(255) not null,
            EVENT_NAME_ varchar(255),
            EXECUTION_ID_ varchar(64),
            PROC_INST_ID_ varchar(64),
            ACTIVITY_ID_ varchar(64),
            CONFIGURATION_ varchar(255),
            CREATED_ timestamp(3) not null DEFAULT CURRENT_TIMESTAMP(3),
            PROC_DEF_ID_ varchar(64),
            SUB_SCOPE_ID_ varchar(64),
            SCOPE_ID_ varchar(64),
            SCOPE_DEFINITION_ID_ varchar(64),
            SCOPE_DEFINITION_KEY_ varchar(255),
            SCOPE_TYPE_ varchar(64),
            LOCK_TIME_ timestamp(3) NULL,
            LOCK_OWNER_ varchar(255),
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_EVENT_SUBSCR_CONFIG_ on ACT_RU_EVENT_SUBSCR(CONFIGURATION_);
        CREATE INDEX ACT_IDX_EVENT_SUBSCR_EXEC_ID on ACT_RU_EVENT_SUBSCR(EXECUTION_ID_);
        CREATE INDEX ACT_IDX_EVENT_SUBSCR_PROC_ID on ACT_RU_EVENT_SUBSCR(PROC_INST_ID_);
        CREATE INDEX ACT_IDX_EVENT_SUBSCR_SCOPEREF_ on ACT_RU_EVENT_SUBSCR(SCOPE_ID_, SCOPE_TYPE_);

        -- org/flowable/db/create/flowable.mysql.create.engine.sql
        CREATE TABLE IF NOT EXISTS ACT_RE_DEPLOYMENT (
            ID_ varchar(64),
            NAME_ varchar(255),
            CATEGORY_ varchar(255),
            KEY_ varchar(255),
            TENANT_ID_ varchar(255) default '',
            DEPLOY_TIME_ timestamp(3) NULL,
            DERIVED_FROM_ varchar(64),
            DERIVED_FROM_ROOT_ varchar(64),
            PARENT_DEPLOYMENT_ID_ varchar(255),
            ENGINE_VERSION_ varchar(255),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RE_MODEL (
            ID_ varchar(64) not null,
            REV_ integer,
            NAME_ varchar(255),
            KEY_ varchar(255),
            CATEGORY_ varchar(255),
            CREATE_TIME_ timestamp(3) null,
            LAST_UPDATE_TIME_ timestamp(3) null,
            VERSION_ integer,
            META_INFO_ varchar(4000),
            DEPLOYMENT_ID_ varchar(64),
            EDITOR_SOURCE_VALUE_ID_ varchar(64),
            EDITOR_SOURCE_EXTRA_VALUE_ID_ varchar(64),
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RU_EXECUTION (
            ID_ varchar(64),
            REV_ integer,
            PROC_INST_ID_ varchar(64),
            BUSINESS_KEY_ varchar(255),
            PARENT_ID_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            SUPER_EXEC_ varchar(64),
            ROOT_PROC_INST_ID_ varchar(64),
            ACT_ID_ varchar(255),
            IS_ACTIVE_ TINYINT,
            IS_CONCURRENT_ TINYINT,
            IS_SCOPE_ TINYINT,
            IS_EVENT_SCOPE_ TINYINT,
            IS_MI_ROOT_ TINYINT,
            SUSPENSION_STATE_ integer,
            CACHED_ENT_STATE_ integer,
            TENANT_ID_ varchar(255) default '',
            NAME_ varchar(255),
            START_ACT_ID_ varchar(255),
            START_TIME_ datetime(3),
            START_USER_ID_ varchar(255),
            LOCK_TIME_ timestamp(3) NULL,
            LOCK_OWNER_ varchar(255),
            IS_COUNT_ENABLED_ TINYINT,
            EVT_SUBSCR_COUNT_ integer,
            TASK_COUNT_ integer,
            JOB_COUNT_ integer,
            TIMER_JOB_COUNT_ integer,
            SUSP_JOB_COUNT_ integer,
            DEADLETTER_JOB_COUNT_ integer,
            EXTERNAL_WORKER_JOB_COUNT_ integer,
            VAR_COUNT_ integer,
            ID_LINK_COUNT_ integer,
            CALLBACK_ID_ varchar(255),
            CALLBACK_TYPE_ varchar(255),
            REFERENCE_ID_ varchar(255),
            REFERENCE_TYPE_ varchar(255),
            PROPAGATED_STAGE_INST_ID_ varchar(255),
            BUSINESS_STATUS_ varchar(255),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RE_PROCDEF (
            ID_ varchar(64) not null,
            REV_ integer,
            CATEGORY_ varchar(255),
            NAME_ varchar(255),
            KEY_ varchar(255) not null,
            VERSION_ integer not null,
            DEPLOYMENT_ID_ varchar(64),
            RESOURCE_NAME_ varchar(4000),
            DGRM_RESOURCE_NAME_ varchar(4000),
            DESCRIPTION_ varchar(4000),
            HAS_START_FORM_KEY_ TINYINT,
            HAS_GRAPHICAL_NOTATION_ TINYINT,
            SUSPENSION_STATE_ integer,
            TENANT_ID_ varchar(255) default '',
            ENGINE_VERSION_ varchar(255),
            DERIVED_FROM_ varchar(64),
            DERIVED_FROM_ROOT_ varchar(64),
            DERIVED_VERSION_ integer not null default 0,
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_EVT_LOG (
            LOG_NR_ bigint auto_increment,
            TYPE_ varchar(64),
            PROC_DEF_ID_ varchar(64),
            PROC_INST_ID_ varchar(64),
            EXECUTION_ID_ varchar(64),
            TASK_ID_ varchar(64),
            TIME_STAMP_ timestamp(3) not null DEFAULT CURRENT_TIMESTAMP(3),
            USER_ID_ varchar(255),
            DATA_ LONGBLOB,
            LOCK_OWNER_ varchar(255),
            LOCK_TIME_ timestamp(3) null,
            IS_PROCESSED_ tinyint default 0,
            primary key (LOG_NR_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_PROCDEF_INFO (
            ID_ varchar(64) not null,
            PROC_DEF_ID_ varchar(64) not null,
            REV_ integer,
            INFO_JSON_ID_ varchar(64),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_RU_ACTINST (
            ID_ varchar(64) not null,
            REV_ integer default 1,
            PROC_DEF_ID_ varchar(64) not null,
            PROC_INST_ID_ varchar(64) not null,
            EXECUTION_ID_ varchar(64) not null,
            ACT_ID_ varchar(255) not null,
            TASK_ID_ varchar(64),
            CALL_PROC_INST_ID_ varchar(64),
            ACT_NAME_ varchar(255),
            ACT_TYPE_ varchar(255) not null,
            ASSIGNEE_ varchar(255),
            COMPLETED_BY_ varchar(255),
            START_TIME_ datetime(3) not null,
            END_TIME_ datetime(3),
            DURATION_ bigint,
            TRANSACTION_ORDER_ integer,
            DELETE_REASON_ varchar(4000),
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE INDEX ACT_IDX_EXEC_BUSKEY on ACT_RU_EXECUTION(BUSINESS_KEY_);
        CREATE INDEX ACT_IDC_EXEC_ROOT on ACT_RU_EXECUTION(ROOT_PROC_INST_ID_);
        CREATE INDEX ACT_IDX_EXEC_REF_ID_ on ACT_RU_EXECUTION(REFERENCE_ID_);
        CREATE INDEX ACT_IDX_VARIABLE_TASK_ID on ACT_RU_VARIABLE(TASK_ID_);
        CREATE INDEX ACT_IDX_ATHRZ_PROCEDEF on ACT_RU_IDENTITYLINK(PROC_DEF_ID_);
        CREATE INDEX ACT_IDX_INFO_PROCDEF on ACT_PROCDEF_INFO(PROC_DEF_ID_);

        CREATE INDEX ACT_IDX_BYTEAR_DEPL on ACT_GE_BYTEARRAY(DEPLOYMENT_ID_);

        CREATE INDEX ACT_IDX_RU_ACTI_START on ACT_RU_ACTINST(START_TIME_);
        CREATE INDEX ACT_IDX_RU_ACTI_END on ACT_RU_ACTINST(END_TIME_);
        CREATE INDEX ACT_IDX_RU_ACTI_PROC on ACT_RU_ACTINST(PROC_INST_ID_);
        CREATE INDEX ACT_IDX_RU_ACTI_PROC_ACT on ACT_RU_ACTINST(PROC_INST_ID_, ACT_ID_);
        CREATE INDEX ACT_IDX_RU_ACTI_EXEC on ACT_RU_ACTINST(EXECUTION_ID_);
        CREATE INDEX ACT_IDX_RU_ACTI_EXEC_ACT on ACT_RU_ACTINST(EXECUTION_ID_, ACT_ID_);
        CREATE INDEX ACT_IDX_RU_ACTI_TASK on ACT_RU_ACTINST(TASK_ID_);

        ALTER TABLE ACT_GE_BYTEARRAY
            add constraint ACT_FK_BYTEARR_DEPL
            foreign key (DEPLOYMENT_ID_)
            references ACT_RE_DEPLOYMENT (ID_);

        ALTER TABLE ACT_RE_PROCDEF
            add constraint ACT_UNIQ_PROCDEF
            unique (KEY_,VERSION_, DERIVED_VERSION_, TENANT_ID_);

        ALTER TABLE ACT_RU_EXECUTION
            add constraint ACT_FK_EXE_PROCINST
            foreign key (PROC_INST_ID_)
            references ACT_RU_EXECUTION (ID_) on delete cascade on update cascade;

        ALTER TABLE ACT_RU_EXECUTION
            add constraint ACT_FK_EXE_PARENT
            foreign key (PARENT_ID_)
            references ACT_RU_EXECUTION (ID_) on delete cascade;

        ALTER TABLE ACT_RU_EXECUTION
            add constraint ACT_FK_EXE_SUPER
            foreign key (SUPER_EXEC_)
            references ACT_RU_EXECUTION (ID_) on delete cascade;

        ALTER TABLE ACT_RU_EXECUTION
            add constraint ACT_FK_EXE_PROCDEF
            foreign key (PROC_DEF_ID_)
            references ACT_RE_PROCDEF (ID_);

        ALTER TABLE ACT_RU_IDENTITYLINK
            add constraint ACT_FK_TSKASS_TASK
            foreign key (TASK_ID_)
            references ACT_RU_TASK (ID_);

        ALTER TABLE ACT_RU_IDENTITYLINK
            add constraint ACT_FK_ATHRZ_PROCEDEF
            foreign key (PROC_DEF_ID_)
            references ACT_RE_PROCDEF(ID_);

        ALTER TABLE ACT_RU_IDENTITYLINK
            add constraint ACT_FK_IDL_PROCINST
            foreign key (PROC_INST_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_TASK
            add constraint ACT_FK_TASK_EXE
            foreign key (EXECUTION_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_TASK
            add constraint ACT_FK_TASK_PROCINST
            foreign key (PROC_INST_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_TASK
              add constraint ACT_FK_TASK_PROCDEF
              foreign key (PROC_DEF_ID_)
              references ACT_RE_PROCDEF (ID_);

        ALTER TABLE ACT_RU_VARIABLE
            add constraint ACT_FK_VAR_EXE
            foreign key (EXECUTION_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_VARIABLE
            add constraint ACT_FK_VAR_PROCINST
            foreign key (PROC_INST_ID_)
            references ACT_RU_EXECUTION(ID_);

        ALTER TABLE ACT_RU_JOB
            add constraint ACT_FK_JOB_EXECUTION
            foreign key (EXECUTION_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_JOB
            add constraint ACT_FK_JOB_PROCESS_INSTANCE
            foreign key (PROCESS_INSTANCE_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_JOB
            add constraint ACT_FK_JOB_PROC_DEF
            foreign key (PROC_DEF_ID_)
            references ACT_RE_PROCDEF (ID_);

        ALTER TABLE ACT_RU_TIMER_JOB
            add constraint ACT_FK_TIMER_JOB_EXECUTION
            foreign key (EXECUTION_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_TIMER_JOB
            add constraint ACT_FK_TIMER_JOB_PROCESS_INSTANCE
            foreign key (PROCESS_INSTANCE_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_TIMER_JOB
            add constraint ACT_FK_TIMER_JOB_PROC_DEF
            foreign key (PROC_DEF_ID_)
            references ACT_RE_PROCDEF (ID_);

        ALTER TABLE ACT_RU_SUSPENDED_JOB
            add constraint ACT_FK_SUSPENDED_JOB_EXECUTION
            foreign key (EXECUTION_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_SUSPENDED_JOB
            add constraint ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE
            foreign key (PROCESS_INSTANCE_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_SUSPENDED_JOB
            add constraint ACT_FK_SUSPENDED_JOB_PROC_DEF
            foreign key (PROC_DEF_ID_)
            references ACT_RE_PROCDEF (ID_);

        ALTER TABLE ACT_RU_DEADLETTER_JOB
            add constraint ACT_FK_DEADLETTER_JOB_EXECUTION
            foreign key (EXECUTION_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_DEADLETTER_JOB
            add constraint ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE
            foreign key (PROCESS_INSTANCE_ID_)
            references ACT_RU_EXECUTION (ID_);

        ALTER TABLE ACT_RU_DEADLETTER_JOB
            add constraint ACT_FK_DEADLETTER_JOB_PROC_DEF
            foreign key (PROC_DEF_ID_)
            references ACT_RE_PROCDEF (ID_);

        ALTER TABLE ACT_RU_EVENT_SUBSCR
            add constraint ACT_FK_EVENT_EXEC
            foreign key (EXECUTION_ID_)
            references ACT_RU_EXECUTION(ID_);

        ALTER TABLE ACT_RE_MODEL
            add constraint ACT_FK_MODEL_SOURCE
            foreign key (EDITOR_SOURCE_VALUE_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RE_MODEL
            add constraint ACT_FK_MODEL_SOURCE_EXTRA
            foreign key (EDITOR_SOURCE_EXTRA_VALUE_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_RE_MODEL
            add constraint ACT_FK_MODEL_DEPLOYMENT
            foreign key (DEPLOYMENT_ID_)
            references ACT_RE_DEPLOYMENT (ID_);

        ALTER TABLE ACT_PROCDEF_INFO
            add constraint ACT_FK_INFO_JSON_BA
            foreign key (INFO_JSON_ID_)
            references ACT_GE_BYTEARRAY (ID_);

        ALTER TABLE ACT_PROCDEF_INFO
            add constraint ACT_FK_INFO_PROCDEF
            foreign key (PROC_DEF_ID_)
            references ACT_RE_PROCDEF (ID_);

        ALTER TABLE ACT_PROCDEF_INFO
            add constraint ACT_UNIQ_INFO_PROCDEF
            unique (PROC_DEF_ID_);

        INSERT IGNORE INTO ACT_GE_PROPERTY
        values ('schema.version', '7.2.0.2', 1);

        INSERT IGNORE INTO ACT_GE_PROPERTY
        values ('schema.history', 'create(7.2.0.2)', 1);

        -- org/flowable/db/create/flowable.mysql.create.history.sql
        CREATE TABLE IF NOT EXISTS ACT_HI_PROCINST (
            ID_ varchar(64) not null,
            REV_ integer default 1,
            PROC_INST_ID_ varchar(64) not null,
            BUSINESS_KEY_ varchar(255),
            PROC_DEF_ID_ varchar(64) not null,
            START_TIME_ datetime(3) not null,
            END_TIME_ datetime(3),
            DURATION_ bigint,
            START_USER_ID_ varchar(255),
            START_ACT_ID_ varchar(255),
            END_ACT_ID_ varchar(255),
            SUPER_PROCESS_INSTANCE_ID_ varchar(64),
            DELETE_REASON_ varchar(4000),
            TENANT_ID_ varchar(255) default '',
            NAME_ varchar(255),
            CALLBACK_ID_ varchar(255),
            CALLBACK_TYPE_ varchar(255),
            REFERENCE_ID_ varchar(255),
            REFERENCE_TYPE_ varchar(255),
            PROPAGATED_STAGE_INST_ID_ varchar(255),
            BUSINESS_STATUS_ varchar(255),
            primary key (ID_),
            unique (PROC_INST_ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_HI_ACTINST (
            ID_ varchar(64) not null,
            REV_ integer default 1,
            PROC_DEF_ID_ varchar(64) not null,
            PROC_INST_ID_ varchar(64) not null,
            EXECUTION_ID_ varchar(64) not null,
            ACT_ID_ varchar(255) not null,
            TASK_ID_ varchar(64),
            CALL_PROC_INST_ID_ varchar(64),
            ACT_NAME_ varchar(255),
            ACT_TYPE_ varchar(255) not null,
            ASSIGNEE_ varchar(255),
            COMPLETED_BY_ varchar(255),
            START_TIME_ datetime(3) not null,
            END_TIME_ datetime(3),
            TRANSACTION_ORDER_ integer,
            DURATION_ bigint,
            DELETE_REASON_ varchar(4000),
            TENANT_ID_ varchar(255) default '',
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_HI_DETAIL (
            ID_ varchar(64) not null,
            TYPE_ varchar(255) not null,
            PROC_INST_ID_ varchar(64),
            EXECUTION_ID_ varchar(64),
            TASK_ID_ varchar(64),
            ACT_INST_ID_ varchar(64),
            NAME_ varchar(255) not null,
            VAR_TYPE_ varchar(255),
            REV_ integer,
            TIME_ datetime(3) not null,
            BYTEARRAY_ID_ varchar(64),
            DOUBLE_ double,
            LONG_ bigint,
            TEXT_ varchar(4000),
            TEXT2_ varchar(4000),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_HI_COMMENT (
            ID_ varchar(64) not null,
            TYPE_ varchar(255),
            TIME_ datetime(3) not null,
            USER_ID_ varchar(255),
            TASK_ID_ varchar(64),
            PROC_INST_ID_ varchar(64),
            ACTION_ varchar(255),
            MESSAGE_ varchar(4000),
            FULL_MSG_ LONGBLOB,
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

        CREATE TABLE IF NOT EXISTS ACT_HI_ATTACHMENT (
            ID_ varchar(64) not null,
            REV_ integer,
            USER_ID_ varchar(255),
            NAME_ varchar(255),
            DESCRIPTION_ varchar(4000),
            TYPE_ varchar(255),
            TASK_ID_ varchar(64),
            PROC_INST_ID_ varchar(64),
            URL_ varchar(4000),
            CONTENT_ID_ varchar(64),
            TIME_ datetime(3),
            primary key (ID_)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;


        CREATE INDEX ACT_IDX_HI_PRO_INST_END on ACT_HI_PROCINST(END_TIME_);
        CREATE INDEX ACT_IDX_HI_PRO_I_BUSKEY on ACT_HI_PROCINST(BUSINESS_KEY_);
        CREATE INDEX ACT_IDX_HI_PRO_SUPER_PROCINST on ACT_HI_PROCINST(SUPER_PROCESS_INSTANCE_ID_);
        CREATE INDEX ACT_IDX_HI_ACT_INST_START on ACT_HI_ACTINST(START_TIME_);
        CREATE INDEX ACT_IDX_HI_ACT_INST_END on ACT_HI_ACTINST(END_TIME_);
        CREATE INDEX ACT_IDX_HI_DETAIL_PROC_INST on ACT_HI_DETAIL(PROC_INST_ID_);
        CREATE INDEX ACT_IDX_HI_DETAIL_ACT_INST on ACT_HI_DETAIL(ACT_INST_ID_);
        CREATE INDEX ACT_IDX_HI_DETAIL_TIME on ACT_HI_DETAIL(TIME_);
        CREATE INDEX ACT_IDX_HI_DETAIL_NAME on ACT_HI_DETAIL(NAME_);
        CREATE INDEX ACT_IDX_HI_DETAIL_TASK_ID on ACT_HI_DETAIL(TASK_ID_);
        CREATE INDEX ACT_IDX_HI_PROCVAR_PROC_INST on ACT_HI_VARINST(PROC_INST_ID_);
        CREATE INDEX ACT_IDX_HI_PROCVAR_TASK_ID on ACT_HI_VARINST(TASK_ID_);
        CREATE INDEX ACT_IDX_HI_PROCVAR_EXE on ACT_HI_VARINST(EXECUTION_ID_);
        CREATE INDEX ACT_IDX_HI_ACT_INST_PROCINST on ACT_HI_ACTINST(PROC_INST_ID_, ACT_ID_);
        CREATE INDEX ACT_IDX_HI_ACT_INST_EXEC on ACT_HI_ACTINST(EXECUTION_ID_, ACT_ID_);
        CREATE INDEX ACT_IDX_HI_IDENT_LNK_TASK on ACT_HI_IDENTITYLINK(TASK_ID_);
        CREATE INDEX ACT_IDX_HI_IDENT_LNK_PROCINST on ACT_HI_IDENTITYLINK(PROC_INST_ID_);
        CREATE INDEX ACT_IDX_HI_TASK_INST_PROCINST on ACT_HI_TASKINST(PROC_INST_ID_);


    ELSEIF v_flowable_table_count < v_expected_flowable_table_count THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Partial Flowable schema detected; repair Flowable tables before running migration_20260521.sql';
    ELSE
        SELECT COUNT(*)
        INTO v_flowable_metadata_count
        FROM ACT_GE_PROPERTY
        WHERE (NAME_ = 'common.schema.version' AND VALUE_ = '7.2.0.2')
           OR (NAME_ = 'schema.version' AND VALUE_ = '7.2.0.2')
           OR (NAME_ = 'schema.history' AND VALUE_ = 'create(7.2.0.2)')
           OR (NAME_ = 'next.dbid' AND VALUE_ IS NOT NULL);

        IF v_flowable_metadata_count < 4 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Flowable schema metadata is not 7.2.0.2; repair Flowable metadata before running migration_20260521.sql';
        END IF;
    END IF;
END //
DELIMITER ;

CALL migrate_flowable_schema_if_missing();
DROP PROCEDURE IF EXISTS migrate_flowable_schema_if_missing;

-- ============================================================
-- 业务表结构
-- ============================================================

-- Lab3 B：普通用户默认个人配额调整为 100MB，VIP 使用 Long.MAX_VALUE 表示不限容量
ALTER TABLE user_quota
    MODIFY COLUMN total_quota BIGINT NOT NULL DEFAULT 104857600
        COMMENT '总配额（字节），普通用户默认100MB，VIP为9223372036854775807';

-- Lab2/Lab3：兼容早期 Lab1 文件表，补齐目录、归属和回收站基础字段后再执行空间迁移
SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN user_id BIGINT DEFAULT NULL COMMENT ''文件所有者ID'' AFTER is_deleted',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'user_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN parent_id BIGINT DEFAULT 0 COMMENT ''父目录ID（0表示根目录）'' AFTER user_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'parent_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN is_directory TINYINT DEFAULT 0 COMMENT ''是否为目录（0=文件 1=目录）'' AFTER parent_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'is_directory'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN full_path VARCHAR(1000) DEFAULT NULL COMMENT ''祖先ID路径（逗号分隔）'' AFTER is_directory',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'full_path'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN file_hash VARCHAR(64) DEFAULT NULL COMMENT ''文件内容 SHA-256，指向 sys_file_object.file_hash'' AFTER full_path',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'file_hash'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN in_recycle_bin TINYINT NOT NULL DEFAULT 0 COMMENT ''是否在回收站（0=正常 1=在回收站）'' AFTER file_hash',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'in_recycle_bin'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN recycle_root TINYINT NOT NULL DEFAULT 0 COMMENT ''是否为回收站根节点（0=否 1=是）'' AFTER in_recycle_bin',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'recycle_root'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD KEY idx_file_hash (file_hash)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_file_hash'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD KEY idx_recycle_bin (user_id, in_recycle_bin, recycle_root)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_recycle_bin'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Lab3 B：文件元数据增加空间归属字段，支持团队空间和私密空间与个人空间隔离
SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN space_type VARCHAR(20) NOT NULL DEFAULT ''PERSONAL'' COMMENT ''空间类型(PERSONAL/TEAM/PRIVATE)'' AFTER user_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'space_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN space_id BIGINT DEFAULT NULL COMMENT ''空间ID（个人为用户ID，团队为团队ID）'' AFTER space_type',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'space_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN uploader_id BIGINT DEFAULT NULL COMMENT ''上传者用户ID'' AFTER space_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'uploader_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_file
SET space_type = 'PERSONAL',
    space_id = user_id,
    uploader_id = user_id
WHERE space_id IS NULL;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD KEY idx_space_parent (space_type, space_id, parent_id, in_recycle_bin, is_deleted)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_space_parent'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD KEY idx_space_name (space_type, space_id, parent_id, original_name, in_recycle_bin, is_deleted)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_space_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD KEY idx_space_trash_root (space_type, space_id, in_recycle_bin, recycle_root, is_deleted)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_space_trash_root'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Lab3 B16：回收站保留期改为显式落库，供列表、恢复校验和后续定时清理使用
SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT ''放入回收站时间'' AFTER recycle_root',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'deleted_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN expire_at DATETIME DEFAULT NULL COMMENT ''回收站到期时间'' AFTER deleted_at',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'expire_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD COLUMN deleted_by BIGINT DEFAULT NULL COMMENT ''删除操作人用户ID'' AFTER deleted_at',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'deleted_by'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_file ADD KEY idx_space_trash_expire (space_type, expire_at, in_recycle_bin, recycle_root, is_deleted)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_space_trash_expire'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_file
SET deleted_at = COALESCE(deleted_at, update_time, create_time, NOW()),
    expire_at = COALESCE(expire_at, DATE_ADD(COALESCE(update_time, create_time, NOW()), INTERVAL 3 DAY))
WHERE in_recycle_bin = 1
  AND is_deleted = 0
  AND (deleted_at IS NULL OR expire_at IS NULL);

UPDATE sys_file
SET deleted_at = NULL,
    expire_at = NULL
WHERE in_recycle_bin = 0
  AND (deleted_at IS NOT NULL OR expire_at IS NOT NULL);

-- Lab3 B：分享记录增加空间归属字段，支持团队分享与个人分享隔离
SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_share ADD COLUMN creator_account_id BIGINT DEFAULT NULL COMMENT ''团队分享创建者账户ID，个人分享为NULL'' AFTER user_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_share' AND COLUMN_NAME = 'creator_account_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_share ADD COLUMN space_type VARCHAR(20) NOT NULL DEFAULT ''PERSONAL'' COMMENT ''分享空间类型(PERSONAL/TEAM)'' AFTER user_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_share' AND COLUMN_NAME = 'space_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_share ADD COLUMN team_id BIGINT DEFAULT NULL COMMENT ''团队ID，个人分享为NULL'' AFTER space_type',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_share' AND COLUMN_NAME = 'team_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_share
SET space_type = 'PERSONAL'
WHERE space_type IS NULL OR space_type = '';

UPDATE sys_share s
LEFT JOIN sys_account a
    ON a.id = (
        SELECT a2.id
        FROM sys_account a2
        WHERE a2.user_id = s.user_id
          AND a2.status = 1
          AND a2.is_deleted = 0
        ORDER BY a2.id ASC
        LIMIT 1
    )
SET s.creator_account_id = COALESCE(s.creator_account_id, a.id, s.user_id)
WHERE s.space_type = 'TEAM'
  AND s.creator_account_id IS NULL
  AND s.is_deleted = 0;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_share ADD KEY idx_team_share (space_type, team_id, is_deleted)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_share' AND INDEX_NAME = 'idx_team_share'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_share ADD KEY idx_team_share_creator (space_type, team_id, creator_account_id, is_deleted)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_share' AND INDEX_NAME = 'idx_team_share_creator'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Lab3 B：私密空间配置表
CREATE TABLE IF NOT EXISTS private_space (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    password_hash   VARCHAR(100) NOT NULL COMMENT '私密空间密码哈希',
    grace_expire_at DATETIME     DEFAULT NULL COMMENT 'VIP降级宽限期截止时间',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT      DEFAULT 0 COMMENT '逻辑删除(0-未删除 1-已删除)',
    UNIQUE KEY uk_private_user (user_id),
    KEY idx_private_user_deleted (user_id, is_deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '私密空间配置表';

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE private_space ADD KEY idx_private_user_deleted (user_id, is_deleted)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'private_space' AND INDEX_NAME = 'idx_private_user_deleted'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 团队空间表
CREATE TABLE IF NOT EXISTS team_space (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name           VARCHAR(100)  NOT NULL COMMENT '团队名称',
    description    VARCHAR(500)  DEFAULT NULL COMMENT '团队描述',
    owner_id       BIGINT        NOT NULL COMMENT '团队Owner用户ID',
    owner_account_id BIGINT      NOT NULL COMMENT '团队Owner账户ID',
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '团队状态(ACTIVE-正常 DISSOLVED-已解散)',
    total_quota    BIGINT        NOT NULL DEFAULT 1073741824 COMMENT '团队总配额（字节），默认1GB',
    used_space     BIGINT        NOT NULL DEFAULT 0 COMMENT '已使用空间（字节）',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted     TINYINT       DEFAULT 0 COMMENT '逻辑删除(0-未删除 1-已删除)'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '团队空间表';

-- 团队成员表
CREATE TABLE IF NOT EXISTS team_member (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    team_id        BIGINT        NOT NULL COMMENT '团队ID',
    user_id        BIGINT        NOT NULL COMMENT '用户ID',
    account_id     BIGINT        NOT NULL COMMENT '账户ID',
    role           VARCHAR(20)   NOT NULL DEFAULT 'Editor' COMMENT '团队角色(Owner/Admin/Editor/Viewer)',
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '成员状态(ACTIVE-正常 REMOVED-已被移除 EXITED-已退出)',
    joined_at      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted     TINYINT       DEFAULT 0 COMMENT '逻辑删除(0-未删除 1-已删除)',
    UNIQUE KEY uk_team_account (team_id, account_id),
    KEY idx_team_user (team_id, user_id),
    KEY idx_account_status (account_id, status, is_deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '团队成员表';

-- 团队邀请表
CREATE TABLE IF NOT EXISTS team_invitation (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    team_id              BIGINT        NOT NULL COMMENT '团队ID',
    inviter_id           BIGINT        NOT NULL COMMENT '邀请人用户ID',
    inviter_account_id   BIGINT        NOT NULL COMMENT '邀请人账户ID',
    invitee_id           BIGINT        NOT NULL COMMENT '被邀请人用户ID',
    invitee_account_id   BIGINT        NOT NULL COMMENT '被邀请人账户ID',
    target_role          VARCHAR(20)   NOT NULL DEFAULT 'Editor' COMMENT '目标角色(Owner/Admin/Editor/Viewer)',
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '邀请状态(PENDING/ACCEPTED/REJECTED/REVOKED/EXPIRED/TEAM_DISSOLVED)',
    expire_at            DATETIME      DEFAULT NULL COMMENT '过期时间',
    flowable_instance_id VARCHAR(100)  DEFAULT NULL COMMENT 'Flowable流程实例ID',
    reason               VARCHAR(500)  DEFAULT NULL COMMENT '拒绝或撤销原因',
    create_time          DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted           TINYINT       DEFAULT 0 COMMENT '逻辑删除(0-未删除 1-已删除)',
    INDEX idx_team_id (team_id),
    INDEX idx_invitee_id (invitee_id),
    INDEX idx_invitee_account_id (invitee_account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '团队邀请表';

-- Lab3 B：团队身份改为账户维度，历史数据按用户的首个启用账户回填
SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_space ADD COLUMN owner_account_id BIGINT DEFAULT NULL COMMENT ''团队Owner账户ID'' AFTER owner_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_space' AND COLUMN_NAME = 'owner_account_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE team_space ts
LEFT JOIN sys_account a
    ON a.id = (
        SELECT a2.id
        FROM sys_account a2
        WHERE a2.user_id = ts.owner_id
          AND a2.status = 1
          AND a2.is_deleted = 0
        ORDER BY a2.id ASC
        LIMIT 1
    )
SET ts.owner_account_id = COALESCE(ts.owner_account_id, a.id, ts.owner_id)
WHERE ts.owner_account_id IS NULL;

SET @sql = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE team_space MODIFY COLUMN owner_account_id BIGINT NOT NULL COMMENT ''团队Owner账户ID''',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'team_space'
      AND COLUMN_NAME = 'owner_account_id'
      AND IS_NULLABLE = 'YES'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_member ADD COLUMN account_id BIGINT DEFAULT NULL COMMENT ''账户ID'' AFTER user_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_member' AND COLUMN_NAME = 'account_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE team_member tm
LEFT JOIN sys_account a
    ON a.id = (
        SELECT a2.id
        FROM sys_account a2
        WHERE a2.user_id = tm.user_id
          AND a2.status = 1
          AND a2.is_deleted = 0
        ORDER BY a2.id ASC
        LIMIT 1
    )
SET tm.account_id = COALESCE(tm.account_id, a.id, tm.user_id)
WHERE tm.account_id IS NULL;

SET @sql = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE team_member MODIFY COLUMN account_id BIGINT NOT NULL COMMENT ''账户ID''',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'team_member'
      AND COLUMN_NAME = 'account_id'
      AND IS_NULLABLE = 'YES'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_member ADD UNIQUE KEY uk_team_account (team_id, account_id)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_member' AND INDEX_NAME = 'uk_team_account'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_member ADD KEY idx_team_user (team_id, user_id)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_member' AND INDEX_NAME = 'idx_team_user'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_member ADD KEY idx_account_status (account_id, status, is_deleted)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_member' AND INDEX_NAME = 'idx_account_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE team_member DROP INDEX uk_team_user',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_member' AND INDEX_NAME = 'uk_team_user'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_invitation ADD COLUMN inviter_account_id BIGINT DEFAULT NULL COMMENT ''邀请人账户ID'' AFTER inviter_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_invitation' AND COLUMN_NAME = 'inviter_account_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_invitation ADD COLUMN invitee_account_id BIGINT DEFAULT NULL COMMENT ''被邀请人账户ID'' AFTER invitee_id',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_invitation' AND COLUMN_NAME = 'invitee_account_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE team_invitation ti
LEFT JOIN sys_account inviter_account
    ON inviter_account.id = (
        SELECT a2.id
        FROM sys_account a2
        WHERE a2.user_id = ti.inviter_id
          AND a2.status = 1
          AND a2.is_deleted = 0
        ORDER BY a2.id ASC
        LIMIT 1
    )
LEFT JOIN sys_account invitee_account
    ON invitee_account.id = (
        SELECT a3.id
        FROM sys_account a3
        WHERE a3.user_id = ti.invitee_id
          AND a3.status = 1
          AND a3.is_deleted = 0
        ORDER BY a3.id ASC
        LIMIT 1
    )
SET ti.inviter_account_id = COALESCE(ti.inviter_account_id, inviter_account.id, ti.inviter_id),
    ti.invitee_account_id = COALESCE(ti.invitee_account_id, invitee_account.id, ti.invitee_id)
WHERE ti.inviter_account_id IS NULL OR ti.invitee_account_id IS NULL;

SET @sql = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE team_invitation MODIFY COLUMN inviter_account_id BIGINT NOT NULL COMMENT ''邀请人账户ID''',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'team_invitation'
      AND COLUMN_NAME = 'inviter_account_id'
      AND IS_NULLABLE = 'YES'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE team_invitation MODIFY COLUMN invitee_account_id BIGINT NOT NULL COMMENT ''被邀请人账户ID''',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'team_invitation'
      AND COLUMN_NAME = 'invitee_account_id'
      AND IS_NULLABLE = 'YES'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_invitation ADD KEY idx_invitee_account_id (invitee_account_id)',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_invitation' AND INDEX_NAME = 'idx_invitee_account_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 团队角色定义表
CREATE TABLE IF NOT EXISTS team_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role        VARCHAR(20) NOT NULL COMMENT '角色标识(Owner/Admin/Editor/Viewer)',
    label       VARCHAR(50) NOT NULL COMMENT '角色显示名称',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT     DEFAULT 0 COMMENT '逻辑删除(0-未删除 1-已删除)',
    UNIQUE KEY uk_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '团队角色定义表';

-- 团队权限点定义表
CREATE TABLE IF NOT EXISTS team_permission (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    permission  VARCHAR(50) NOT NULL COMMENT '权限点标识',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT     DEFAULT 0 COMMENT '逻辑删除(0-未删除 1-已删除)',
    UNIQUE KEY uk_permission (permission)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '团队权限点定义表';

-- 团队角色-权限关联表
CREATE TABLE IF NOT EXISTS team_role_permission (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_id       BIGINT   NOT NULL COMMENT '角色ID，关联 team_role.id',
    permission_id BIGINT   NOT NULL COMMENT '权限ID，关联 team_permission.id',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted    TINYINT  DEFAULT 0 COMMENT '逻辑删除(0-未删除 1-已删除)',
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '团队角色-权限关联表';

-- 初始化角色数据
INSERT IGNORE INTO team_role (role, label) VALUES
('Owner', '拥有者'), ('Admin', '管理员'), ('Editor', '编辑者'), ('Viewer', '只读者');

-- 初始化权限点数据
INSERT IGNORE INTO team_permission (permission) VALUES
('team:manage'), ('team:dissolve'), ('owner:transfer'),
('member:invite'), ('member:remove'), ('role:update'),
('file:list'), ('file:detail'), ('file:download'),
('file:upload'), ('file:move'), ('file:copy'), ('file:delete'),
('share:create'), ('share:manage'),
('trash:list'), ('trash:restore'), ('trash:delete'),
('file:transfer:to-personal'), ('file:transfer:to-team');

-- 初始化角色-权限关联数据
INSERT IGNORE INTO team_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM team_role r, team_permission p WHERE r.role = 'Owner' AND p.permission IN (
    'team:manage', 'team:dissolve', 'owner:transfer',
    'member:invite', 'member:remove', 'role:update',
    'file:list', 'file:detail', 'file:download',
    'file:upload', 'file:move', 'file:copy', 'file:delete',
    'share:create', 'share:manage',
    'trash:list', 'trash:restore', 'trash:delete',
    'file:transfer:to-personal', 'file:transfer:to-team'
);

INSERT IGNORE INTO team_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM team_role r, team_permission p WHERE r.role = 'Admin' AND p.permission IN (
    'team:manage',
    'member:invite', 'member:remove', 'role:update',
    'file:list', 'file:detail', 'file:download',
    'file:upload', 'file:move', 'file:copy', 'file:delete',
    'share:create', 'share:manage',
    'trash:list', 'trash:restore', 'trash:delete',
    'file:transfer:to-personal', 'file:transfer:to-team'
);

INSERT IGNORE INTO team_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM team_role r, team_permission p WHERE r.role = 'Editor' AND p.permission IN (
    'file:list', 'file:detail', 'file:download',
    'file:upload', 'file:move', 'file:copy', 'file:delete',
    'share:create'
);

INSERT IGNORE INTO team_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM team_role r, team_permission p WHERE r.role = 'Viewer' AND p.permission IN (
    'file:list', 'file:detail', 'file:download'
);
