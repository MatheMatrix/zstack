-- Fix: alarm UUID modification | ZSV-7837
delete from `SystemTagVO` where resourceUuid = '5z6gsgkc5kccpylj9ocgbd647p2700b7';
delete from `AlarmActionVO` where alarmUuid = '5z6gsgkc5kccpylj9ocgbd647p2700b7';
delete from `AlarmVO` where uuid = '5z6gsgkc5kccpylj9ocgbd647p2700b7';
delete from `ResourceVO` where uuid = '5z6gsgkc5kccpylj9ocgbd647p2700b7';
update `AlarmRecordsVO` set alarmUuid = '00d9435038505e5f90af167cfacbf611' where alarmUuid = '5z6gsgkc5kccpylj9ocgbd647p2700b7';

delete from `SystemTagVO` where resourceUuid = 'uhgfoh0soh6e1qai005elfa9c6h2s2y0';
delete from `AlarmActionVO` where alarmUuid = 'uhgfoh0soh6e1qai005elfa9c6h2s2y0';
delete from `AlarmVO` where uuid = 'uhgfoh0soh6e1qai005elfa9c6h2s2y0';
delete from `ResourceVO` where uuid = 'uhgfoh0soh6e1qai005elfa9c6h2s2y0';
update `AlarmRecordsVO` set alarmUuid = '00d78dac81854d86a056fae8d0913711' where alarmUuid = 'uhgfoh0soh6e1qai005elfa9c6h2s2y0';

delete from `SystemTagVO` where resourceUuid = 'fuz2p4fa71urf4fd7cknoxsalvj60ynk';
delete from `AlarmActionVO` where alarmUuid = 'fuz2p4fa71urf4fd7cknoxsalvj60ynk';
delete from `AlarmVO` where uuid = 'fuz2p4fa71urf4fd7cknoxsalvj60ynk';
delete from `ResourceVO` where uuid = 'fuz2p4fa71urf4fd7cknoxsalvj60ynk';
update `AlarmRecordsVO` set alarmUuid = '00d781a5db9542e2b0c6ddd62e0c4811' where alarmUuid = 'fuz2p4fa71urf4fd7cknoxsalvj60ynk';

delete from `SystemTagVO` where resourceUuid = 'd0b35ac37c58e358cb74e664532f1044';
delete from `AlarmActionVO` where alarmUuid = 'd0b35ac37c58e358cb74e664532f1044';
delete from `AlarmVO` where uuid = 'd0b35ac37c58e358cb74e664532f1044';
delete from `ResourceVO` where uuid = 'd0b35ac37c58e358cb74e664532f1044';
update `AlarmRecordsVO` set alarmUuid = '00d78ee6b5914dee9d6ac82b37207711' where alarmUuid = 'd0b35ac37c58e358cb74e664532f1044';

delete from `SystemTagVO` where resourceUuid = '6nz3vn2e0rdwu5hzmuetzv37ak0nj248';
delete from `EventSubscriptionActionVO` where subscriptionUuid = '6nz3vn2e0rdwu5hzmuetzv37ak0nj248';
delete from `EventSubscriptionVO` where uuid = '6nz3vn2e0rdwu5hzmuetzv37ak0nj248';
delete from `ResourceVO` where uuid = '6nz3vn2e0rdwu5hzmuetzv37ak0nj248';
update `EventRecordsVO` set subscriptionUuid = '00ddf8d0949a4618a08693a2977e1211' where subscriptionUuid = '6nz3vn2e0rdwu5hzmuetzv37ak0nj248';

delete from `SystemTagVO` where resourceUuid = 'ppfazo1y3tjvup4jfetxz36y3su98ngc';
delete from `EventSubscriptionActionVO` where subscriptionUuid = 'ppfazo1y3tjvup4jfetxz36y3su98ngc';
delete from `EventSubscriptionVO` where uuid = 'ppfazo1y3tjvup4jfetxz36y3su98ngc';
delete from `ResourceVO` where uuid = 'ppfazo1y3tjvup4jfetxz36y3su98ngc';
update `EventRecordsVO` set subscriptionUuid = '00d6f58ce9b34437bd0ee1ba4c27c511' where subscriptionUuid = 'ppfazo1y3tjvup4jfetxz36y3su98ngc';

delete from `SystemTagVO` where resourceUuid = 'rlwalvvqyoujj3ign3o309p2zulwbhwm';
delete from `EventSubscriptionActionVO` where subscriptionUuid = 'rlwalvvqyoujj3ign3o309p2zulwbhwm';
delete from `EventSubscriptionVO` where uuid = 'rlwalvvqyoujj3ign3o309p2zulwbhwm';
delete from `ResourceVO` where uuid = 'rlwalvvqyoujj3ign3o309p2zulwbhwm';
update `EventRecordsVO` set subscriptionUuid = '00d2b5ffc6d54371b9d31ef513447411' where subscriptionUuid = 'rlwalvvqyoujj3ign3o309p2zulwbhwm';

delete from `SystemTagVO` where resourceUuid = 'krdu1hs2314kt18ttgqndaynxchs2ufc';
delete from `EventSubscriptionActionVO` where subscriptionUuid = 'krdu1hs2314kt18ttgqndaynxchs2ufc';
delete from `EventSubscriptionVO` where uuid = 'krdu1hs2314kt18ttgqndaynxchs2ufc';
delete from `ResourceVO` where uuid = 'krdu1hs2314kt18ttgqndaynxchs2ufc';
update `EventRecordsVO` set subscriptionUuid = '00d1218c839442d69c08eb54ba2c5b11' where subscriptionUuid = 'krdu1hs2314kt18ttgqndaynxchs2ufc';

delete from `SystemTagVO` where resourceUuid = '8tlwqj65mus1gdolu3w61yy35pvwinhz';
delete from `EventSubscriptionActionVO` where subscriptionUuid = '8tlwqj65mus1gdolu3w61yy35pvwinhz';
delete from `EventSubscriptionVO` where uuid = '8tlwqj65mus1gdolu3w61yy35pvwinhz';
delete from `ResourceVO` where uuid = '8tlwqj65mus1gdolu3w61yy35pvwinhz';
update `EventRecordsVO` set subscriptionUuid = '00dd2d1d987f4dc79e98b052126af611' where subscriptionUuid = '8tlwqj65mus1gdolu3w61yy35pvwinhz';

delete from `SystemTagVO` where resourceUuid = 'g0eviogong06nubt1kj54z63pcka81sw';
delete from `EventSubscriptionActionVO` where subscriptionUuid = 'g0eviogong06nubt1kj54z63pcka81sw';
delete from `EventSubscriptionVO` where uuid = 'g0eviogong06nubt1kj54z63pcka81sw';
delete from `ResourceVO` where uuid = 'g0eviogong06nubt1kj54z63pcka81sw';
update `EventRecordsVO` set subscriptionUuid = '00d4a85f56394800aa9bdfbf555e0911' where subscriptionUuid = 'g0eviogong06nubt1kj54z63pcka81sw';

delete from `SystemTagVO` where resourceUuid = '559ca06aa8bba6990d10c255e4c9ab5b';
delete from `EventSubscriptionActionVO` where subscriptionUuid = '559ca06aa8bba6990d10c255e4c9ab5b';
delete from `EventSubscriptionVO` where uuid = '559ca06aa8bba6990d10c255e4c9ab5b';
delete from `ResourceVO` where uuid = '559ca06aa8bba6990d10c255e4c9ab5b';
update `EventRecordsVO` set subscriptionUuid = '00dca06aa8bb16998d10c255e4c9ab11' where subscriptionUuid = '559ca06aa8bba6990d10c255e4c9ab5b';

delete from `SystemTagVO` where resourceUuid = '33198a88f22e4d19b5ff8ebaebb6ujm7';
delete from `AlarmActionVO` where alarmUuid = '33198a88f22e4d19b5ff8ebaebb6ujm7';
delete from `AlarmVO` where uuid = '33198a88f22e4d19b5ff8ebaebb6ujm7';
delete from `ResourceVO` where uuid = '33198a88f22e4d19b5ff8ebaebb6ujm7';
update `AlarmRecordsVO` set alarmUuid = '00da493006cb4668a46c09f9d4914c11' where alarmUuid = '33198a88f22e4d19b5ff8ebaebb6ujm7';

delete from `SystemTagVO` where resourceUuid = 'a678a66daed67879b5ef2166aaedc07b';
delete from `EventSubscriptionActionVO` where subscriptionUuid = 'a678a66daed67879b5ef2166aaedc07b';
delete from `EventSubscriptionVO` where uuid = 'a678a66daed67879b5ef2166aaedc07b';
delete from `ResourceVO` where uuid = 'a678a66daed67879b5ef2166aaedc07b';
update `EventRecordsVO` set subscriptionUuid = '00dd520eb0dc4c828b95c3cd6b01fb11' where subscriptionUuid = 'a678a66daed67879b5ef2166aaedc07b';

delete from `SystemTagVO` where resourceUuid = 'a678a66daed24779bbgf2166aaedc07b';
delete from `EventSubscriptionActionVO` where subscriptionUuid = 'a678a66daed24779bbgf2166aaedc07b';
delete from `EventSubscriptionVO` where uuid = 'a678a66daed24779bbgf2166aaedc07b';
delete from `ResourceVO` where uuid = 'a678a66daed24779bbgf2166aaedc07b';
update `EventRecordsVO` set subscriptionUuid = '00d633549c444c99be4890f096ce8e11' where subscriptionUuid = 'a678a66daed24779bbgf2166aaedc07b';

delete from `SystemTagVO` where resourceUuid = 'a678a66daed24779b5ef2zaqaaedc07b';
delete from `EventSubscriptionActionVO` where subscriptionUuid = 'a678a66daed24779b5ef2zaqaaedc07b';
delete from `EventSubscriptionVO` where uuid = 'a678a66daed24779b5ef2zaqaaedc07b';
delete from `ResourceVO` where uuid = 'a678a66daed24779b5ef2zaqaaedc07b';
update `EventRecordsVO` set subscriptionUuid = '00d0eaaeb12d40879c81cd207b83bc11' where subscriptionUuid = 'a678a66daed24779b5ef2zaqaaedc07b';

delete from `SystemTagVO` where resourceUuid = '5z6gsgkc5kccpylj9234fd647p2700b7';
delete from `AlarmActionVO` where alarmUuid = '5z6gsgkc5kccpylj9234fd647p2700b7';
delete from `AlarmVO` where uuid = '5z6gsgkc5kccpylj9234fd647p2700b7';
delete from `ResourceVO` where uuid = '5z6gsgkc5kccpylj9234fd647p2700b7';
update `AlarmRecordsVO` set alarmUuid = '00df1327d49e4631a21f4467aa729c11' where alarmUuid = '5z6gsgkc5kccpylj9234fd647p2700b7';

delete from `SystemTagVO` where resourceUuid = 'a678a66da6093b5ef2166aaedc07b';
delete from `AlarmActionVO` where alarmUuid = 'a678a66da6093b5ef2166aaedc07b';
delete from `AlarmVO` where uuid = 'a678a66da6093b5ef2166aaedc07b';
delete from `ResourceVO` where uuid = 'a678a66da6093b5ef2166aaedc07b';
update `AlarmRecordsVO` set alarmUuid = '00dded12d01f464599abc47e5d9c8f11' where alarmUuid = 'a678a66da6093b5ef2166aaedc07b';
