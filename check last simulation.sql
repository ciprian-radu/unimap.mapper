SELECT *
FROM unimap_sibiudb.MAPPER
WHERE
	TIMEDIFF(START_DATETIME, '2011-05-16 09:00:00') > 0
--	AND
--	NAME = 'sa'
--	NAME = 'sa_test-attraction_move'
ORDER BY
	START_DATETIME
;

SELECT M.NAME, M.BENCHMARK, M.APCG_ID, COUNT(APCG_ID)
FROM unimap_sibiudb.MAPPER M, unimap_sibiudb.PARAMETER P
WHERE
	M.RUN = P.ID
	AND
	P.NAME = 'routing'
	AND
	(P.VALUE = 'true' OR P.VALUE = 'false')
	AND
	TIMEDIFF(START_DATETIME, '2011-01-25 16:30:00') > 0
GROUP BY M.NAME, M.BENCHMARK, M.APCG_ID
ORDER BY M.NAME, M.BENCHMARK, M.APCG_ID
;

SELECT SUM(COUNTER) AS HOW_MANY_SIMULATIONS_FAILED
FROM
		(
		SELECT 1000 - COUNT(APCG_ID) AS COUNTER
		FROM unimap_sibiudb.MAPPER M, unimap_sibiudb.PARAMETER P
		WHERE
			M.RUN = P.ID
			AND
			P.NAME = 'routing'
			AND
			(P.VALUE = 'true' OR P.VALUE = 'false')
			AND
			TIMEDIFF(START_DATETIME, '2011-01-25 16:30:00') > 0
		GROUP BY M.NAME, M.BENCHMARK, M.APCG_ID, P.VALUE
		HAVING
			1000 - COUNT(APCG_ID) >= 0
		ORDER BY COUNT(APCG_ID)
		) AS SOME_ALIAS
;

SELECT M1.MAPPING_XML, O1.VALUE, COUNT(O1.VALUE)
FROM unimap_sibiudb.MAPPER M1, unimap_sibiudb.OUTPUT O1, unimap_sibiudb.PARAMETER P1
WHERE
	TIMEDIFF(M1.START_DATETIME, '2011-02-07 10:00:00') > 0
	AND
--	M1.NAME = 'sa'
--	M1.NAME = 'sa_test'
	M1.NAME = 'sa_test-attraction_move'
--	M1.NAME = 'bb'
	AND
	M1.RUN = O1.ID
	AND
	O1.NAME = 'energy'
	AND
	M1.RUN = P1.ID
	AND
	P1.NAME = 'routing'
	AND
	P1.VALUE = 'false'
GROUP BY
	O1.VALUE
ORDER BY
	O1.VALUE
;

SELECT
-- *
-- AVG(O.VALUE)
-- AVG(M.USER_TIME)
AVG(M.AVG_HEAP_MEMORY)
FROM unimap_sibiudb.MAPPER M, unimap_sibiudb.OUTPUT O, unimap_sibiudb.PARAMETER P
WHERE
	TIMEDIFF(M.START_DATETIME, '2011-01-25 16:30:00') > 0
	AND
--	M.NAME = 'sa_test-attraction_move'
--	M.NAME = 'sa_test'
--	M.NAME = 'sa'
	M.NAME = 'bb'
	AND
	M.RUN = O.ID
	AND
	O.NAME = 'energy'
	AND
	M.RUN = P.ID
	AND
	P.NAME = 'routing'
	AND
	P.VALUE = 'false'
ORDER BY
--	M.START_DATETIME
	O.VALUE
;






































