ALTER TABLE SystemTagVO ADD COLUMN effectiveMode varchar(256) DEFAULT NULL;
ALTER TABLE GlobalConfigVO ADD COLUMN effectiveMode varchar(256) DEFAULT NULL;
ALTER TABLE ResourceConfigVO ADD COLUMN effectiveMode varchar(256) DEFAULT NULL;
